// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.agent

import com.example.daysurpopt.domain.Defaults
import com.example.daysurpopt.domain.FinancialInput
import com.example.daysurpopt.domain.SpecificExpense
import com.example.daysurpopt.domain.SurplusInput
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REAL integration test: interrogates the app's agent exactly like the chat does —
 * same system prompt (safety + PromptConstructor), same message flow, same
 * AgentToolExecutor tool loop. Skips silently when no API key is available.
 *
 * Provider resolution (first with WORKING credentials wins; 401/402/429 quota errors
 * fall through to the next candidate):
 * - OPENROUTER_API_KEY (env) -> https://openrouter.ai (the app's default provider)
 * - OPENAI_API_KEY / DEEPSEEK_API_KEY / GEMINI_API_KEY -> OpenAI-compatible endpoints,
 *   used to validate the agent harness when no OpenRouter key is configured.
 */
class AgentOpenRouterHarnessTest {

    private val gson = Gson()

    private data class Provider(val name: String, val url: String, val key: String, val model: String, val referer: String)

    private class ProviderUnavailableException(message: String) : RuntimeException(message)

    private fun candidateProviders(): List<Provider> {
        val providers = mutableListOf<Provider>()
        System.getenv("OPENROUTER_API_KEY")?.takeIf { it.isNotBlank() }?.let {
            providers.add(
                Provider(
                    "OpenRouter", "https://openrouter.ai/api/v1/chat/completions", it,
                    System.getenv("OPENROUTER_MODEL") ?: "qwen/qwen3.7-plus",
                    "https://github.com/FinancialAwareness"
                )
            )
        }
        System.getenv("OPENAI_API_KEY")?.takeIf { it.isNotBlank() }?.let {
            providers.add(
                Provider("OpenAI", "https://api.openai.com/v1/chat/completions", it,
                    System.getenv("OPENAI_MODEL") ?: "gpt-4o-mini", "")
            )
        }
        System.getenv("DEEPSEEK_API_KEY")?.takeIf { it.isNotBlank() }?.let {
            providers.add(
                Provider("DeepSeek", "https://api.deepseek.com/chat/completions", it,
                    System.getenv("DEEPSEEK_MODEL") ?: "deepseek-chat", "")
            )
        }
        System.getenv("GEMINI_API_KEY")?.takeIf { it.isNotBlank() }?.let {
            providers.add(
                Provider(
                    "Gemini", "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions", it,
                    System.getenv("GEMINI_MODEL") ?: "gemini-2.0-flash", ""
                )
            )
        }
        return providers
    }

    private fun callLlm(provider: Provider, messages: List<Map<String, String>>): String {
        val body = mapOf("model" to provider.model, "messages" to messages, "stream" to false)
        val connection = (java.net.URI(provider.url).toURL().openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${provider.key}")
            if (provider.referer.isNotBlank()) {
                setRequestProperty("HTTP-Referer", provider.referer)
                setRequestProperty("X-Title", "FinancialAwareness")
            }
            connectTimeout = 60_000
            readTimeout = 90_000
        }
        connection.outputStream.use { it.write(gson.toJson(body).toByteArray()) }
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        val raw = stream?.bufferedReader()?.readText().orEmpty()
        val code = connection.responseCode
        if (code == 401 || code == 402 || code == 403 || code == 429) {
            throw ProviderUnavailableException("HTTP $code from ${provider.name}: ${raw.take(200)}")
        }
        check(code in 200..299) { "HTTP $code from ${provider.name}: ${raw.take(300)}" }

        val parsed = gson.fromJson(raw, Map::class.java) as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val choices = parsed["choices"] as? List<Map<*, *>> ?: error("No choices in: ${raw.take(300)}")
        @Suppress("UNCHECKED_CAST")
        val message = choices.first()["message"] as? Map<*, *> ?: error("No message in response")
        return message["content"] as? String ?: error("No content in response")
    }

    private suspend fun runScenario(provider: Provider): Pair<List<String>, String> {
        val inputs = FinancialInput().withDefaultAssumptionCurves()
        val expenses = emptyList<SpecificExpense>()
        val surplus = SurplusInput(mutuoAffitto = 600.0, mutuoAffittoFinoEta = 60)

        val systemPrompt = Defaults.OPENROUTER_SAFETY_SYSTEM_PROMPT.trim() + "\n\n" +
            PromptConstructor.constructSystemPrompt(inputs, expenses, surplus)

        val messages = mutableListOf(
            mapOf("role" to "system", "content" to systemPrompt),
            mapOf(
                "role" to "user",
                "content" to
                    "Step 1: use GET_FINANCIAL_CONTEXT and tell me the y value of my effective degradation " +
                    "curve at the highest age. " +
                    "Step 2: run RUN_SIMULATION with degradationCurvePoints = [{\"x\":30,\"y\":1.0}," +
                    "{\"x\":70,\"y\":1.0},{\"x\":82,\"y\":0.6}] and tell me the new Final Capital."
            )
        )

        val executedTools = mutableListOf<String>()
        var finalAnswer = ""
        run loop@{
            repeat(5) {
                val response = callLlm(provider, messages)
                println("=== [${provider.name}] LLM RESPONSE ===\n$response\n")
                val toolOutput = AgentToolExecutor.checkForToolUse(
                    response = response,
                    baseInputs = inputs,
                    specificExpenses = expenses,
                    surplusData = surplus,
                    alreadyExecutedCommands = executedTools.toSet(),
                    llmRequest = { "multi-agent not under test" }
                )
                if (toolOutput == null) {
                    finalAnswer = response
                    return@loop
                }
                val command = AgentToolExecutor.extractCommandName(response)
                if (command != null) executedTools.add(command)
                println("=== [${provider.name}] TOOL OUTPUT ($command) ===\n$toolOutput\n")
                messages.add(mapOf("role" to "assistant", "content" to response))
                messages.add(mapOf("role" to "system", "content" to toolOutput))
            }
            error("Agent did not produce a final answer within the tool-loop budget")
        }
        return Pair(executedTools, finalAnswer)
    }

    @Test
    fun agent_reads_curves_and_runs_what_if_simulation_with_real_llm() = runBlocking {
        // Opt-in: real LLM calls are non-deterministic and must not destabilize the suite.
        // Run manually with AGENT_HARNESS_E2E=1 in the environment.
        org.junit.Assume.assumeTrue(
            "Skipped: set AGENT_HARNESS_E2E=1 (plus an API key env var) to run the real-agent integration test",
            System.getenv("AGENT_HARNESS_E2E") == "1"
        )
        val candidates = candidateProviders()
        assertTrue(
            "No OPENROUTER_API_KEY / OPENAI_API_KEY / DEEPSEEK_API_KEY / GEMINI_API_KEY in env: skipping real-agent test",
            candidates.isNotEmpty()
        )

        var executedTools: List<String>? = null
        var finalAnswer = ""
        var usedProvider: Provider? = null
        val unavailable = mutableListOf<String>()
        for (provider in candidates) {
            try {
                val (tools, answer) = runScenario(provider)
                executedTools = tools
                finalAnswer = answer
                usedProvider = provider
                break
            } catch (e: ProviderUnavailableException) {
                println("=== PROVIDER UNAVAILABLE: ${e.message} ===")
                unavailable.add("${provider.name}: ${e.message?.take(120) ?: "unknown"}")
            }
        }
        assertTrue(
            "All candidate providers were unavailable:\n${unavailable.joinToString("\n")}",
            executedTools != null
        )

        println("=== VALIDATED WITH: ${usedProvider!!.name} | EXECUTED TOOLS: $executedTools ===")
        assertTrue(
            "Agent must read the curves via GET_FINANCIAL_CONTEXT, executed: $executedTools",
            "GET_FINANCIAL_CONTEXT" in executedTools!!
        )
        assertTrue(
            "Agent must test the curve edit via RUN_SIMULATION, executed: $executedTools",
            "RUN_SIMULATION" in executedTools
        )
        assertTrue(
            "Final answer must report a Final Capital number:\n$finalAnswer",
            Regex("Final Capital", RegexOption.IGNORE_CASE).containsMatchIn(finalAnswer) &&
                Regex("\\d{2,}([.,]\\d+)?").containsMatchIn(finalAnswer)
        )
    }
}
