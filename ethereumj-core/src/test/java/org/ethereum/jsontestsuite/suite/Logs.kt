/*
 * The MIT License (MIT)
 *
 * Copyright 2017 Alexander Orlov <alexander.orlov@loxal.net>. All rights reserved.
 * Copyright (c) [2016] [ <ether.camp> ]
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package org.ethereum.jsontestsuite.suite

import org.ethereum.vm.DataWord
import org.ethereum.vm.LogInfo
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.spongycastle.util.encoders.Hex
import java.util.*

class Logs(jLogs: JSONArray) {
    private val logs = ArrayList<LogInfo>()

    init {

        for (jLog1 in jLogs) {

            val jLog = jLog1 as JSONObject
            val address = Hex.decode(jLog["address"] as String)
            val data = Hex.decode((jLog["data"] as String).substring(2))

            val topics = ArrayList<DataWord>()

            val jTopics = jLog["topics"] as JSONArray
            for (t in jTopics.toTypedArray()) {
                val topic = Hex.decode(t as String)
                topics.add(DataWord(topic))
            }

            val li = LogInfo(address, topics, data)
            logs.add(li)
        }
    }


    val iterator: Iterator<LogInfo>
        get() = logs.iterator()


    fun compareToReal(logs: List<LogInfo>): List<String> {

        val results = ArrayList<String>()

        var i = 0
        for (postLog in this.logs) {

            val realLog = logs[i]

            val postAddress = Hex.toHexString(postLog.address)
            val realAddress = Hex.toHexString(realLog.address)

            if (postAddress != realAddress) {

                val formattedString = String.format("Log: %s: has unexpected address, expected address: %s found address: %s",
                        i, postAddress, realAddress)
                results.add(formattedString)
            }

            val postData = Hex.toHexString(postLog.data)
            val realData = Hex.toHexString(realLog.data)

            if (postData != realData) {

                val formattedString = String.format("Log: %s: has unexpected data, expected data: %s found data: %s",
                        i, postData, realData)
                results.add(formattedString)
            }

            val postBloom = Hex.toHexString(postLog.bloom.data)
            val realBloom = Hex.toHexString(realLog.bloom.data)

            if (postData != realData) {

                val formattedString = String.format("Log: %s: has unexpected bloom, expected bloom: %s found bloom: %s",
                        i, postBloom, realBloom)
                results.add(formattedString)
            }

            val postTopics = postLog.topics
            val realTopics = realLog.topics

            var j = 0
            for (postTopic in postTopics) {

                val realTopic = realTopics[j]

                if (postTopic != realTopic) {

                    val formattedString = String.format("Log: %s: has unexpected topic: %s, expected topic: %s found topic: %s",
                            i, j, postTopic, realTopic)
                    results.add(formattedString)
                }
                ++j
            }

            ++i
        }

        return results
    }

}
