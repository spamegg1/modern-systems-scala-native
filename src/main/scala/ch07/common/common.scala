package ch07

import collection.mutable.{Map => MMap}

// used in curlSync, curlAsync
case class ResponseState(
    var code: Int = 200,
    var headers: MMap[String, String] = MMap(),
    var body: String = ""
)
