package job;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import messenger.Slack;
import model.Flight;
import model.NewFlightMonitor;

public class FlightSearchJob implements Runnable {

	private static final String FLIGHT_SERVICE = System.getenv("FLIGHT_SERVICE");
	private static final String BASE_TARGET = System.getenv("BASE_TARGET");
	private static final String FIREBASE_AUTH = System.getenv("FIREBASE_AUTH");
	private static final long SLEEP_TIME_BETWEEN_CICLES = Long.parseLong(System.getenv("SLEEP_TIME_BETWEEN_CICLES"));
	private static final int MSG_INFO_AFTER_N_TMES = Integer.parseInt(System.getenv("MSG_INFO_AFTER_N_TIMES"));
	private static final long SLEEP_TIME_BETWEEN_FLIGHTS = Long.parseLong(System.getenv("SLEEP_TIME_BETWEEN_FLIGHTS"));
	private static final int CONNECTION_TIMEOUT = Integer.parseInt(System.getenv("CONNECTION_TIMEOUT"));
	private static final String URL_MONITOR_FLIGHT = System.getenv("MONITOR_FLIGHTS");
	private static final String GET = "GET";
	private static final String ZONE_ID = "GMT-03:00";
	private static final String CHARSET = "UTF-8";

	public FlightSearchJob() {
	}

	@Override
	public void run() {

		int counter = 1;
		while (true) {
			List<NewFlightMonitor> flights = checkFlights();
			CloseableHttpClient client = null;
			for (NewFlightMonitor fm : flights) {
				try {
					System.out.println("NewFlightMonitor: " + fm);

					DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd/MM/yyyy");

					LocalDate start = LocalDate.parse(fm.getDtStart(), dtf);
					LocalDate end = LocalDate.parse(fm.getDtEnd(), dtf);
					int minDays = fm.getMinDays();
					int maxDays = fm.getMaxDays();

					if (start.plusDays(maxDays).isAfter(end)) {
						System.err.println("Intervalo maior que data final");
						continue;
					}

					int diff = maxDays - minDays;
					int period = (int) ChronoUnit.DAYS.between(start, end) - minDays;
					for (int i = 0; i <= period; i++) {
						for (int j = 0; j <= diff; j++) {
							LocalDate newStart = start.plusDays(i);
							LocalDate newEnd = newStart.plusDays(minDays + j);

							if (!newEnd.isAfter(end)) {

								DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy");

								String dtDep = newStart.format(formatter);
								String dtRet = newEnd.format(formatter);
								String nonStop = fm.isNonStop() ? "NS" : "-";
								String target = BASE_TARGET + "busca/voos-resultados#/" + fm.getFrom() + "/"
										+ fm.getTo() + "/RT/" + dtDep + "/" + dtRet + "/-/-/-/" + fm.getAdult() + "/"
										+ fm.getChild() + "/0/" + nonStop + "/-/-/-";

								System.out.println("target=" + target);

								client = buildHttpClient();
								HttpPost post = new HttpPost(FLIGHT_SERVICE);
								List<NameValuePair> urlParameters = new ArrayList<NameValuePair>();
								urlParameters.add(new BasicNameValuePair("target", target));
								post.setEntity(new UrlEncodedFormEntity(urlParameters));
								HttpResponse response = client.execute(post);
								String body = EntityUtils.toString(response.getEntity(), CHARSET);
								int codeResponse = response.getStatusLine().getStatusCode();
								boolean isError = codeResponse > 400;

								String now = getCurrentDateTime();

								if (isError) {
									if (codeResponse < 500) {
										Flight flt = new Gson().fromJson(body, Flight.class);
										new Slack().sendMessage("[" + now + "] Ocorreu o seguinte erro: " + flt.getMsg()
												+ "\nURL: " + target, Slack.ERROR);
									} else {
										new Slack().sendMessage("[" + now + "] Ocorreu o seguinte erro: " + codeResponse
												+ " - " + body + "\nURL: " + target, Slack.ERROR);
									}
									System.err.println("Ocorreu o seguinte erro: " + codeResponse + " - " + body);
								} else {
									Flight betterFlight = new Gson().fromJson(body, Flight.class);
									float lowerPrice = getFloat(betterFlight.getPrice());

									System.out.println("[" + now + "] " + body);

									if (fm.getAlertPrice() > lowerPrice) {
										String msg = "[" + now + "] Comprar voo de " + fm.getFrom() + " para "
												+ fm.getTo() + " da " + betterFlight.getCia() + " por " + lowerPrice
												+ " no período de " + dtDep + " a " + dtRet + " para " + fm.getAdult()
												+ " adulto(s) e " + fm.getChild() + " criança(s)";

										System.out.println("[" + now + "] " + msg);

										Slack slack = new Slack();
										String resp = slack.sendMessage(msg, Slack.ALERT);
										System.out.println(
												"[" + now + "] Resposta da mensagem enviada pelo Slack: " + resp);
									}
								}

								try {
									Thread.sleep(SLEEP_TIME_BETWEEN_FLIGHTS);
								} catch (InterruptedException e) {
									new Slack().sendMessage("[" + now + "] Erro: " + e.getMessage(), Slack.ERROR);
									System.err.println("[" + now + "] Erro: " + e.getMessage());
								}

							}
						}
					}
				} catch (Exception e) {
					Slack slack = new Slack();
					slack.sendMessage("Erro: " + e.getMessage(), Slack.ERROR);
					System.err.println("Erro: " + e.getMessage());
				} finally {
					if (client != null) {
						try {
							client.close();
						} catch (IOException e) {
						}
					}
				}

				try {
					Thread.sleep(SLEEP_TIME_BETWEEN_FLIGHTS);
				} catch (InterruptedException e) {
					String now = getCurrentDateTime();
					new Slack().sendMessage("[" + now + "] Erro: " + e.getMessage(), Slack.ERROR);
					System.err.println("[" + now + "] Erro: " + e.getMessage());
				}
			}

			String now = getCurrentDateTime();
			System.out.println("[" + now + "] Ciclo " + counter);

			if ((counter % MSG_INFO_AFTER_N_TMES) == 0) {
				new Slack().sendMessage("[" + now + "] I'm working too!", Slack.INFO);
			}

			// Reset counter
			if (counter++ > 10000)
				counter = 1;

			// Aguardar um pouco antes de reiniciar o ciclo de pesquisas
			try {
				Thread.sleep(SLEEP_TIME_BETWEEN_CICLES);
			} catch (InterruptedException e) {
				new Slack().sendMessage("[" + now + "] Erro: " + e.getMessage(), Slack.ERROR);
				System.err.println("[" + now + "] Erro: " + e.getMessage());
			}
		}

	}

	/**
	 * Listar os voos que devem ser monitorados
	 * 
	 * @return Lista de voos
	 */
	private List<NewFlightMonitor> checkFlights() {
		List<NewFlightMonitor> retorno = new ArrayList<NewFlightMonitor>();
		HttpURLConnection connection = null;
		InputStream is = null;
		try {
			URL url = new URL(URL_MONITOR_FLIGHT + "?auth=" + FIREBASE_AUTH);
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod(GET);
			connection.setConnectTimeout(CONNECTION_TIMEOUT);
			connection.setUseCaches(false);
			connection.setDoInput(true);
			connection.setDoOutput(true);

			int codeResponse = connection.getResponseCode();

			boolean isError = codeResponse >= 400;
			is = isError ? connection.getErrorStream() : connection.getInputStream();
			String contentEncoding = connection.getContentEncoding() != null ? connection.getContentEncoding()
					: CHARSET;
			String response = IOUtils.toString(is, contentEncoding);

			if (!isError) {
				Gson gson = new Gson();
				JsonObject obj = new JsonParser().parse(response).getAsJsonObject();
				for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
					NewFlightMonitor flight = gson.fromJson(entry.getValue(), NewFlightMonitor.class);
					retorno.add(flight);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException e) {
				}
			}
			if (connection != null) {
				connection.disconnect();
			}
		}

		return retorno;
	}

	/**
	 * Configurar o cliente HTTP que fará o request
	 * 
	 * @return Cliente HTTP configurado
	 */
	private static CloseableHttpClient buildHttpClient() {
		RequestConfig globalConfig = RequestConfig.custom().setCookieSpec(CookieSpecs.DEFAULT)
				.setConnectionRequestTimeout(CONNECTION_TIMEOUT).setConnectTimeout(CONNECTION_TIMEOUT)
				.setSocketTimeout(CONNECTION_TIMEOUT).build();
		CookieStore cookieStore = new BasicCookieStore();

		return HttpClients.custom().setDefaultRequestConfig(globalConfig).setDefaultCookieStore(cookieStore).build();
	}

	/**
	 * Obter data e hora corrente
	 * 
	 * @return Data e hora corrente no formato dd/MM/yyyy HH:mm:ss
	 */
	private String getCurrentDateTime() {
		ZoneId zoneId = ZoneId.of(ZONE_ID);
		LocalDateTime now = LocalDateTime.now(zoneId);
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
		return now.format(formatter);
	}

	/**
	 * Converter o preço passado como parâmetro para o tipo float
	 * 
	 * @param price
	 * @return
	 */
	private float getFloat(String price) {
		float retorno = 100000;

		String tmp = price.substring(3);
		try {
			retorno = Float.parseFloat(tmp.replace(".", ""));
		} catch (NumberFormatException e) {
		}

		return retorno;
	}

}
