package com.hercules.Welplet;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class WebServer {
	private static final int SERVER_PORT_NUMBER = 8080;
	private ServerSocket mMainSocket;

	WebServer() {
		try {
			mMainSocket = new ServerSocket(SERVER_PORT_NUMBER);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void run() {
		while (true) {
			// Listen for a TCP connection request.

			try {
				Socket connection = mMainSocket.accept();

				// Construct an object to process the HTTP request message.
				HTTPRequestHandler request = new HTTPRequestHandler(connection);

				// Create a new thread to process the request.
				Thread thread = new Thread(request);

				// Start the thread.
				thread.start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

}
