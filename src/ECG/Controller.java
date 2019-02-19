package ECG;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.AnchorPane;
import static jssc.SerialPort.MASK_RXCHAR;
import jssc.SerialPortEvent;
import jssc.SerialPortException;
import jssc.SerialPortList;
import jssc.SerialPort;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;

public class Controller {
    private final int NUM_OF_POINT = 199;
    private XYChart.Series series;
    private SerialPort arduinoPort = null;
    private String selectedPort = "";
    private String selectedRate = "";
    private boolean disconnected = false;
    private byte[] b;
    private Alert alert = new Alert(Alert.AlertType.ERROR);
    private Writer wr;
    private long timestamp;
    private long start_time;
    private JSONArray buffer;
    private static String URI = "http://10.3.1.89:8080/ecg/add";
    private static String CONTENT = "application/json";
    private Task timerTask= new Task();
    private StringBuilder sb = new StringBuilder();
    private Timer timer = new Timer();

    @FXML
    private Button btnConnect;
    @FXML
    private Button btnDisconnect;
    @FXML
    private Button btnQuit;
    @FXML
    private ComboBox<String> cmbPort;
    @FXML
    private ComboBox<String> cmbBaudRate;
    @FXML
    private Button btnRefresh;
    @FXML
    private AnchorPane AP1;
    @FXML
    LineChart<Number, Number> line_Chart;
    @FXML


    private void connectClick(ActionEvent event) {
        disconnected = true;
        if (selectedRate.length() == 0 && selectedPort.length() != 0) {
            alert.setTitle("ECG");
            alert.setHeaderText("Hey!, there is something missing.");
            alert.setContentText("Select the Baud Rate to start the Connection");
            alert.showAndWait();
        } else if (selectedPort.length() == 0 && selectedRate.length() != 0) {
            alert.setTitle("ECG");
            alert.setHeaderText("Hey!, there is something missing.");
            alert.setContentText("Select the Serial Port to start the Connection");
            alert.showAndWait();
        } else if (selectedPort.length() == 0 && selectedRate.length() == 0) {
            alert.setTitle("ECG");
            alert.setHeaderText("Hey!, there is something missing.");
            alert.setContentText("Select the Serial Port and the Baud Rate to start the Connection");
            alert.showAndWait();
        } else {
            disconnected = false;
            try {
                wr = new FileWriter("/Users/jorgeduardo/Desktop/output.csv");
                wr.write("timestamp; value");
                wr.write(System.lineSeparator());
            } catch (IOException e) {
                e.printStackTrace();
            }
            connectArduino(selectedPort, Integer.parseInt(selectedRate));
            btnConnect.setDisable(true);
            btnRefresh.setDisable(true);
            btnDisconnect.setDisable(false);
        }
    }
    @FXML
    void disconnectClick(ActionEvent event) {
        disconnected = true;
        disconnectArduino();
        File oldfile = new File("/Users/jorgeduardo/Desktop/output.csv");
        File newfile = new File("/Users/jorgeduardo/Desktop/output_" + timestamp + ".csv");
        oldfile.renameTo(newfile);
        oldfile.delete();
        btnConnect.setDisable(false);
        btnDisconnect.setDisable(true);
        btnRefresh.setDisable(false);
    }
    @FXML
    void quitClick(ActionEvent event) {
        if (disconnected) {
            Platform.exit();
            System.exit(0);
        } else {
            disconnectArduino();
            File oldfile = new File("/Users/jorgeduardo/Desktop/output.csv");
            File newfile = new File("/Users/jorgeduardo/Desktop/output_" + timestamp + ".csv");
            Platform.exit();
            System.exit(0);
        }
    }
    @FXML
    void refreshClick(ActionEvent event) {
        cmbPort.getItems().clear();
        String[] ports = SerialPortList.getPortNames();
        cmbPort.getItems().addAll(ports);
    }
    @FXML
    private void initialize() {

        InetAddress ip;
        try {
            ip = InetAddress.getLocalHost();
            NetworkInterface network = NetworkInterface.getByInetAddress(ip);
            byte[] mac = network.getHardwareAddress();
            for (int i = 0; i < mac.length; i++) {
                sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? ":" : ""));
            }
        } catch (UnknownHostException | SocketException e) {
            e.printStackTrace();
        }

        buffer = new JSONArray();

        disconnected = false;
        btnDisconnect.setDisable(true);
        cmbBaudRate.getItems().addAll("9600", "14400", "19200", "28800", "38400", "57600", "115200");
        String[] ports = SerialPortList.getPortNames();
        cmbPort.getItems().addAll(ports);
        cmbPort.valueProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable,
                                String oldValue, String newValue) {
                selectedPort = newValue;
            }
        });
        cmbBaudRate.valueProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable,
                                String oldValue, String newValue) {
                selectedRate = newValue;
            }
        });
        series = new XYChart.Series();
        line_Chart.getData().add(series);
        line_Chart.setAnimated(false);
        line_Chart.setCreateSymbols(false);
        line_Chart.getStyleClass().add("thick-chart");
        for (int i = 0; i < NUM_OF_POINT; i++) {
            series.getData().add(new XYChart.Data(i, 0));
        }
    }
    private void connectArduino(String port, int baud) {
        SerialPort serialPort = new SerialPort(port);
        try {
            timer = new Timer();
            timerTask=new Task();
            timer.scheduleAtFixedRate(timerTask, 1000, 10000);
            serialPort.openPort();
            serialPort.setParams(baud, 8, 1, 0);
            serialPort.setEventsMask(MASK_RXCHAR);
            serialPort.addEventListener((SerialPortEvent serialPortEvent) -> {
                if (serialPortEvent.isRXCHAR()) {
                    try {
                        b = serialPort.readBytes();
                        int value = b[0] & 0xff;    //convert to int
                        String output = Integer.toString(value);
                        timestamp = new Date().getTime();

                        if (buffer.length()==0){
                            start_time=timestamp;
                        }

                        JSONObject object= new JSONObject();
                        object.put("timestamp",timestamp);
                        object.put("value",value);
                        buffer.put(object);

                        try {
                            wr.write(timestamp + "; " + output);
                            wr.write(System.lineSeparator());

                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        Platform.runLater(() -> {
                            shiftSeriesData((float) value);
                        });
                    } catch (SerialPortException ex) {
                        Logger.getLogger(Main.class.getName())
                                .log(Level.SEVERE, null, ex);
                    }
                }
            });
            arduinoPort = serialPort;
        } catch (SerialPortException ex) {
            Logger.getLogger(Main.class.getName())
                    .log(Level.SEVERE, null, ex);
            System.out.println("SerialPortException: " + ex.toString());
        }
    }
    private void disconnectArduino() {
        timer.cancel();
        timerTask.cancel();
        if (arduinoPort != null) {
            try {
                arduinoPort.removeEventListener();
                if (arduinoPort.isOpened()) {
                    arduinoPort.closePort();
                    try {
                        wr.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            } catch (SerialPortException ex) {
                Logger.getLogger(Main.class.getName())
                        .log(Level.SEVERE, null, ex);
            }
        }
    }
    private void shiftSeriesData(float newValue) {
        for (int i = 0; i < NUM_OF_POINT - 1; i++) {
            XYChart.Data<Number, Number> ShiftDataUp = (XYChart.Data<Number, Number>) series.getData().get(i + 1);
            Number shiftValue = ShiftDataUp.getYValue();
            XYChart.Data<Number, Number> ShiftDataDn = (XYChart.Data<Number, Number>) series.getData().get(i);
            ShiftDataDn.setYValue(shiftValue);
        }
        XYChart.Data<Number, Number> lastData = (XYChart.Data<Number, Number>) series.getData().get(NUM_OF_POINT - 1);
        lastData.setYValue(newValue);
    }

    private void sendData(JSONObject jsonObject){
    new Thread(new Runnable() {
        @Override
        public void run() {
            HttpURLConnection urlConnection = null;
            try {

                urlConnection = (HttpURLConnection) ((new URL(URI ).openConnection()));
                urlConnection.setDoOutput(true);
                urlConnection.setDoInput(true);
                urlConnection.setRequestProperty("Content-Type", CONTENT);
                urlConnection.setRequestProperty("Accept", CONTENT);
                urlConnection.setRequestMethod("POST");
                urlConnection.connect();
                DataOutputStream dataOutputStream1 = new DataOutputStream(urlConnection.getOutputStream());
                BufferedWriter writer1 = new BufferedWriter(new OutputStreamWriter(dataOutputStream1, StandardCharsets.UTF_8));
                writer1.write(jsonObject.toString());
                writer1.close();
                dataOutputStream1.close();
                int response_code = urlConnection.getResponseCode();
                System.out.println("***************************");
                System.out.println("Service Response Code: " + response_code);
                System.out.println("***************************");

            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }
        }
    }).start();
    }

  private class Task extends TimerTask{
      @Override
      public void run() {
          JSONObject json= new JSONObject();
          json.put("ecg_id",sb.toString());
          json.put("values",buffer);
          json.put("start_time",start_time);
          json.put("end_time",new Date().getTime());
          System.out.println(json.toString(1));
          sendData(json);
          buffer=new JSONArray();
      }
    }

    }