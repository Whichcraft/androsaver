package com.androsaver

import com.androsaver.source.GoogleDrivePages
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GoogleDrivePagesTest {
    @Test fun repeatedCursorsStopWithoutRepeatingRequestsForever() = runBlocking {
        var requests = 0
        val files = GoogleDrivePages().list {
            requests++
            JsonParser.parseString("""{"files":[],"nextPageToken":"repeat"}""").asJsonObject
        }
        assertTrue(files.isEmpty())
        assertEquals(2, requests)
    }

    @Test fun emptyPagesStillConsumeThePageBudget() = runBlocking {
        var requests = 0
        GoogleDrivePages(maxPages = 3).list {
            requests++
            JsonParser.parseString("""{"files":[],"nextPageToken":"next-$requests"}""").asJsonObject
        }
        assertEquals(3, requests)
    }

    @Test fun malformedEntriesStillConsumeTheScanBudget() = runBlocking {
        var requests = 0
        val files = GoogleDrivePages(maxScanned = 3).list {
            requests++
            JsonParser.parseString("""{"files":[{},null],"nextPageToken":"next-$requests"}""").asJsonObject
        }
        assertTrue(files.isEmpty())
        assertEquals(2, requests)
    }

    @Test fun duplicatesDoNotConsumeTheAcceptedImageBudget() = runBlocking {
        var requests = 0
        val files = GoogleDrivePages(maxItems = 2).list {
            requests++
            JsonParser.parseString(if (requests == 1) {
                """{"files":[{"id":"a","name":"first"},{"id":"a","name":"duplicate"}],"nextPageToken":"second"}"""
            } else {
                """{"files":[{"id":"b","name":"second"},{"id":"c","name":"third"}],"nextPageToken":"third"}"""
            }).asJsonObject
        }
        assertEquals(listOf("a", "b"), files.map { it.id })
        assertEquals(2, requests)
    }
}
