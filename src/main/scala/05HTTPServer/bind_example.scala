/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
val server_socket = socket(AF_INET, SOCK_STREAM, 0)

val server_address = stackalloc[sockaddr_in]
!server_address._1 = AF_INET.toUShort  // IP Socket
!server_address._2 = htons(port)       // port
!server_address._3._1 = INADDR_ANY     // bind to 0.0.0.0
val server_sockaddr = server_address.cast[Ptr[sockaddr]]
val addr_size = sizeof[sockaddr_in].toUInt

val bind_result = bind(server_socket, server_sockaddr, addr_size)
println(s"bind returned $bind_result")
