package dev.wick.activity.nuke

import dev.wick.utils.WeLogger
import dev.wick.utils.serialization.DefaultJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

private const val TAG = "NukeGitHubContributors"
private const val CONTRIBUTORS_URL =
    ""

data class NukeGitHubContributor(
    val login: String,
    val profileUrl: String,
    val avatarUrl: String,
    val contributionCount: Int? = null,
)

/** Public GitHub contributor data for Nuke's About page. */
object NukeGitHubContributors {
    /**
     * Empty on purpose: this build ships no contributor list, and the live fetch has no endpoint
     * configured either. The About page must therefore tolerate an empty result.
     */
    val fallbackContributors: List<NukeGitHubContributor> = emptyList()

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetchOrFallback(): List<NukeGitHubContributor> = withContext(Dispatchers.IO) {
        try {
            fetchContributors()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            WeLogger.w(TAG, "failed to fetch GitHub contributors; using fallback", error)
            fallbackContributors
        }
    }

    private fun fetchContributors(): List<NukeGitHubContributor> {
        val request = Request.Builder()
            .url(CONTRIBUTORS_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            // GitHub requires a UA but nothing about it; do not advertise the module.
            .header("User-Agent", "okhttp/4.12.0")
            .build()
        val contributors = httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "GitHub contributors request failed: HTTP ${response.code}" }
            DefaultJson.decodeFromString<List<GitHubContributorResponse>>(response.body.string())
        }.asSequence()
            .filter { it.type == "User" && it.login.isNotBlank() && it.avatarUrl.isNotBlank() }
            .map { contributor ->
                NukeGitHubContributor(
                    login = contributor.login,
                    profileUrl = contributor.profileUrl.ifBlank {
                        "https://github.com/${contributor.login}"
                    },
                    avatarUrl = contributor.avatarUrl,
                    contributionCount = contributor.contributions,
                )
            }
            .distinctBy { it.login.lowercase() }
            .toList()

        check(contributors.isNotEmpty()) { "GitHub contributors response had no user accounts" }
        return contributors
    }
}

private fun fallbackContributor(login: String) = NukeGitHubContributor(
    login = login,
    profileUrl = "https://github.com/$login",
    avatarUrl = "https://github.com/$login.png?size=160",
)

@Serializable
private data class GitHubContributorResponse(
    val login: String,
    @SerialName("html_url") val profileUrl: String,
    @SerialName("avatar_url") val avatarUrl: String,
    val type: String,
    val contributions: Int,
)
