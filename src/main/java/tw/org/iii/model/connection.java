/*
 * @Author:Andy Peng
 */
package tw.org.iii.model;

/**
 *
 * @author andy
 */

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class connection {

		public void changeIP() throws InterruptedException, IOException {
				Process p = Runtime.getRuntime().exec("rasdial.exe 寬頻連線 /disconnect");
				p.waitFor();

				p = Runtime.getRuntime().exec("rasdial.exe 寬頻連線 75329524@hinet.net fdlyngsp");
				p.waitFor();
				Thread.sleep(3000);
		}

		public String request(String url, String type) throws IOException {
				StringBuilder results = new StringBuilder();
				try {
						URL myURL = new URL(url);
						HttpURLConnection connection = (HttpURLConnection) myURL.openConnection();
						connection.setRequestMethod("GET");
						connection.setDoOutput(true);
						connection.setRequestProperty("User-Agent", "Mozilla/5.0");
						connection.connect();

						BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), type));

						String line;
						while ((line = reader.readLine()) != null) {
								results.append(line);
						}

						connection.disconnect();
						Thread.sleep(500);
				} catch (Exception e) {
						e.printStackTrace();
				}

				return results.toString();

		}

}

