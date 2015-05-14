package com.hercules.Welplet;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.StringTokenizer;

public class HTTPRequestHandler implements Runnable {
	final static int LF = (int) '\n';
	private static final String DEFAULT_FILE = "./default.html";
	private static final String CRLF = "\r\n";
	private Socket mConnectedSocket;
	private InputStream mSocketInputStream;
	private DataOutputStream mSocketDataOutputStream;
	private HTTPRequestMessage RequestMessage;

	private class HTTPRequestMessage {
		private int mLength;
		private byte[] mMessageData;
		private int mCurrentIndex;
		private String mBoundary;

		public HTTPRequestMessage(byte[] messageData) {
			mMessageData = messageData;
			mLength = messageData.length;
			mCurrentIndex = 0;
			mBoundary = "";
		}

		private String readLine() {
			if (mCurrentIndex >= mLength) {
				return null;
			}
			StringBuilder sb = new StringBuilder();
			while ((int) mMessageData[mCurrentIndex] != LF) {
				sb.append((char) mMessageData[mCurrentIndex]);
				mCurrentIndex++;

				if (mCurrentIndex >= mLength) {
					break;
				}
			}
			// set index to the beginning of the next line
			mCurrentIndex++;
			return sb.toString();
		}

		private byte[] getFileContent() {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			boolean shouldContinue = true;

			while (shouldContinue) {
				while ((int) mMessageData[mCurrentIndex] != (int) '-') {
					baos.write(mMessageData[mCurrentIndex]);
					mCurrentIndex++;
				}

				String temp = readLine();
				if (temp.equals(mBoundary)) {
					shouldContinue = false;
				} else {
					mCurrentIndex = mCurrentIndex - temp.length() - 1;
					baos.write(mMessageData[mCurrentIndex]);
					mCurrentIndex++;
				}

			}
			debug("fileSize: " + baos.toByteArray().length);
			return baos.toByteArray();

		}

		public int getLength() {
			return mLength;
		}

		public byte[] getMessageData() {
			return mMessageData;
		}

		public int getCurrentIndex() {
			return mCurrentIndex;
		}

		public void setCurrentIndex(int currentIndex) {
			mCurrentIndex = currentIndex;
		}

		public String getBoundary() {
			return mBoundary;
		}

		public void setBoundary(String boundary) {
			mBoundary = boundary;
		}

	}

	public HTTPRequestHandler(Socket connectedSocket) {
		mConnectedSocket = connectedSocket;
		try {
			mSocketDataOutputStream = new DataOutputStream(
					mConnectedSocket.getOutputStream());
			mSocketInputStream = mConnectedSocket.getInputStream();

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		readRequest();
		parseRequest();

		try {
			mSocketDataOutputStream.close();
			mSocketInputStream.close();
			mConnectedSocket.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private void parseRequest() {
		String readLine = RequestMessage.readLine() + "\n";

		StringTokenizer stk = new StringTokenizer(readLine);
		String method = stk.nextToken();

		if (method.equals("POST")) {
			handlePost();
		} else if (method.equals("GET")) {
			String fileName = "." + stk.nextToken();
			handleGet(fileName);
		}
	}

	private void handleGet(String fileName) {

		sendResponseAsFile(fileName);

	}

	private void sendResponseAsFile(String fileName) {
		if (fileName.equals("./"))
			fileName = DEFAULT_FILE;
		try {
			FileInputStream fileTobeRead = new FileInputStream(fileName);
			// HTTP header
			String HTTPHeader = "HTTP/1.0 200 OK" + CRLF + "Content-Type: "
					+ contentType(fileName) + CRLF + CRLF;

			mSocketDataOutputStream.writeBytes(HTTPHeader);

			byte[] buffer = new byte[1024];
			int bytes = 0;

			// Copy requested file into the socket's output stream.

			while ((bytes = fileTobeRead.read(buffer)) != -1) {
				mSocketDataOutputStream.write(buffer, 0, bytes);
			}
			fileTobeRead.close();

		} catch (FileNotFoundException e) {
			String BadResponse = "HTTP/1.0 404 Not Found" + CRLF
					+ "Content-Type: text/html" + CRLF + CRLF + "<HTML>"
					+ "<HEAD><TITLE>Not Found</TITLE></HEAD>"
					+ "<BODY>Not Found</BODY></HTML>";

			try {
				mSocketDataOutputStream.writeBytes(BadResponse);
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

		} catch (IOException e) {

		}
	}

	private void handlePost() {

		while (RequestMessage.getCurrentIndex() < RequestMessage.getLength()) {
			String readLine = RequestMessage.readLine();
			if (readLine.contains("multipart/form-data")) {
				// Line has the form
				// Content-Type: multipart/form-data; boundary=--someboundary

				StringTokenizer tokenizer = new StringTokenizer(readLine);
				tokenizer.nextToken("=");
				String boundary = "--" + tokenizer.nextToken("=");
				RequestMessage.setBoundary(boundary);

				handleMultipartPost();
			}
		}

		sendResponseAsFile("./");

		// increment counter to the beginning of the next line

	}

	private void handleMultipartPost() {
		while (!RequestMessage.readLine().equals(RequestMessage.getBoundary())) {
		}
		String readLine = RequestMessage.readLine();
		if (readLine.contains("someFile")) {
			// Line has the form
			// Content-Disposition: form-data; name="someFile";
			// filename="somefilename.someext"
			StringTokenizer tokenizer = new StringTokenizer(readLine);

			tokenizer.nextToken("=");
			tokenizer.nextToken("=");
			String fileName = tokenizer.nextToken("=");
			// Remove quotation marks
			fileName = fileName.substring(1, fileName.length() - 2);

			handleFileImport(fileName);

		}

	}

	private void handleFileImport(String fileName) {
		// Skip Content-Type and black line
		RequestMessage.readLine();
		RequestMessage.readLine();
		try {
			FileOutputStream postedFile = new FileOutputStream(fileName);
			postedFile.write(RequestMessage.getFileContent());
			postedFile.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	private void debug(String toDebug) {
		System.out.println(toDebug);
	}

	private static String contentType(String fileName) {
		if (fileName.endsWith(".htm") || fileName.endsWith(".html")) {
			return "text/html";
		}
		if (fileName.endsWith(".ram") || fileName.endsWith(".ra")) {
			return "audio/x-pn-realaudio";
		}
		return "application/octet-stream";
	}

	private void readRequest() {
		try {
			byte[] buffer = new byte[1024];
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			int bytesRead = 0;
			do {
				bytesRead = mSocketInputStream.read(buffer);
				baos.write(buffer, 0, bytesRead);

			} while (bytesRead >= 1024);

			RequestMessage = new HTTPRequestMessage(baos.toByteArray());
			System.out.println(new String(RequestMessage.getMessageData()));
		} catch (IOException e) {

		}
	}
}