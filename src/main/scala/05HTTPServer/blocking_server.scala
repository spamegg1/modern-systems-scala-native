/***
 * Excerpted from "Modern Systems Programming with Scala Native",
 * published by The Pragmatic Bookshelf.
 * Copyrights apply to this code. It may not be used to create training material,
 * courses, books, articles, and the like. Contact us if you are in doubt.
 * We make no guarantees that this code is fit for any purpose.
 * Visit http://www.pragmaticprogrammer.com/titles/rwscala for more book information.
***/
def serve(port:UShort): Unit = {
    // Allocate and initialize the server address
    val addr_size = sizeof[sockaddr_in]
    val server_address = malloc(addr_size).cast[Ptr[sockaddr_in]]
    !server_address._1 = AF_INET.toUShort  // IP Socket
    !server_address._2 = htons(port)       // port
    !server_address._3._1 = INADDR_ANY     // bind to 0.0.0.0

    // Bind and listen on a socket
    val sock_fd = socket(AF_INET, SOCK_STREAM, 0)
    val server_sockaddr = server_address.cast[Ptr[sockaddr]]
    val bind_result = bind(sock_fd, server_sockaddr, addr_size.toUInt)
    println(s"bind returned $bind_result")
    val listen_result = listen(sock_fd, 128)
    println(s"listen returned $listen_result")
    println(s"accepting connections on port $port")

    // Main accept() loop
    while (true) {
        val conn_fd = accept(sock_fd, null, null)
        println(s"accept returned fd $conn_fd")
        // we will replace handle_connection with fork_and_handle shortly
        handle_connection(conn_fd)
    }
    close(sock_fd)
}

def handle_connection(conn_socket:Int, max_size:Int = 1024): Unit = {
    val message =
      c"Connection accepted!  Enter a message and it will be echoed back\n"

    val prompt_write = write(conn_socket, message, strlen(message))

    val line_buffer = malloc(max_size)
    while (true) {
        val bytes_read = read(conn_socket, line_buffer, max_size)
        println(s"read $bytes_read bytes")
        if (bytes_read == EOF)
            // This means the connection has been closed by the client
            return
        val bytes_written = write(conn_socket, line_buffer, bytes_read)
        println(s"wrote $bytes_written bytes")
    }
}
