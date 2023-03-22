/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
val client_address = stackalloc[sockaddr_in]
val client_sockaddr = client_address.cast[Ptr[sockaddr]]
val client_addr_size = stackalloc[UInt]
!client_addr_size = sizeof[sockaddr_in].toUInt

val connection_socket = accept(server_socket, client_sockaddr,
                               client_addr_size)
println(
  s"""accept returned fd $connection_socket;
     |connected client address is
     |${format_sockaddr_in(client_address)}""".stripMargin
)
