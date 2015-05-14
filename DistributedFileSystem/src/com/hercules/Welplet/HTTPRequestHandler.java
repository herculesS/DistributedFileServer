package com.hercules.Welplet;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.util.StringTokenizer;

public class HTTPRequestHandler implements Runnable {
	final static int LF = (int) '\n';
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

	}

	private void parseRequest() {
		String readLine = RequestMessage.readLine() + "\n";

		StringTokenizer stk = new StringTokenizer(readLine);
		String method = stk.nextToken();

		if (method.equals("POST")) {
			handlePost();
		} else if (method.equals("GET")) {
			String fileName = stk.nextToken();
			handleGet(fileName);
		}
	}

	private void handleGet(String fileName) {
		// TODO Auto-generated method stub

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