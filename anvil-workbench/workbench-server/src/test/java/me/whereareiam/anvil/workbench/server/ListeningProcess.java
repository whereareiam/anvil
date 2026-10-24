package me.whereareiam.anvil.workbench.server;

import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * A process the tests run through a workbench's execution session: it listens on the port it is given, reports
 * that it is ready, greets one connection and exits.
 */
public final class ListeningProcess {
	public static void main(String[] arguments) throws Exception {
		try (ServerSocket listener = new ServerSocket(Integer.parseInt(arguments[0]), 50, InetAddress.getLoopbackAddress())) {
			System.out.println("READY");
			System.out.flush();
			try (Socket client = listener.accept(); OutputStream output = client.getOutputStream()) {
				output.write("hello\n".getBytes(StandardCharsets.UTF_8));
			}
		}
	}
}
