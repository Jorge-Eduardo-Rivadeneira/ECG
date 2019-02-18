//Controller.java
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
import java.io.*;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Controller {

    private final int NUM_OF_POINT = 199;
    private XYChart.Series series;
    private SerialPort arduinoPort = null;
    private String selectedPort="";
    private String selectedRate="";
    private boolean disconnected;
    private byte[] b;
    private Alert alert = new Alert(Alert.AlertType.ERROR);
    private Writer wr;
    private long timestamp;

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
    void connectClick(ActionEvent event) {
        disconnected=true;
        if (selectedRate.length()==0 && selectedPort.length()!=0){

            alert.setTitle("ECG");
            alert.setHeaderText("Hey!, there is something missing.");
            alert.setContentText("Select the Baud Rate to start the Connection");
            alert.showAndWait(); }

        else if  (selectedPort.length()==0 && selectedRate.length()!=0) {

            alert.setTitle("ECG");
            alert.setHeaderText("Hey!, there is something missing.");
            alert.setContentText("Select the Serial Port to start the Connection");
            alert.showAndWait();}

        else if (selectedPort.length()==0 && selectedRate.length()==0) {

            alert.setTitle("ECG");
            alert.setHeaderText("Hey!, there is something missing.");
            alert.setContentText("Select the Serial Port and the Baud Rate to start the Connection");
            alert.showAndWait(); }

        else {
            if (disconnected==true){
                disconnected = false;
                try {
                    wr = new FileWriter("/Users/jorgeduardo/Desktop/output.csv");
                    wr.write("timestamp; value");
                    wr.write(System.lineSeparator());

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            connectArduino(selectedPort,Integer.parseInt(selectedRate));
            btnConnect.setDisable(true);
            btnRefresh.setDisable(true);
            btnDisconnect.setDisable(false);
        }
    }

    @FXML
    void disconnectClick(ActionEvent event) {
        disconnected = true;
        disconnectArduino();
        File oldfile =new File("/Users/jorgeduardo/Desktop/output.csv");
        File newfile =new File( "/Users/jorgeduardo/Desktop/output_"+timestamp+".csv");
        oldfile.renameTo(newfile);
        oldfile.delete();
        btnConnect.setDisable(false);
        btnDisconnect.setDisable(true);
        btnRefresh.setDisable(false);
    }
    @FXML
    void quitClick(ActionEvent event) {
        if(disconnected==true){

            Platform.exit();
            System.exit(0);
        } else if(disconnected==false){
            disconnectArduino();
            File oldfile =new File("/Users/jorgeduardo/Desktop/output.csv");
            File newfile =new File( "/Users/jorgeduardo/Desktop/output_"+timestamp+".csv");
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
    public void initialize() {
        disconnected=false;
        btnDisconnect.setDisable(true);
        cmbBaudRate.getItems().addAll("9600", "14400", "19200", "28800", "38400", "57600", "115200");
        String[] ports = SerialPortList.getPortNames();
        cmbPort.getItems().addAll(ports);

        cmbPort.valueProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable,
                                String oldValue, String newValue) {
                selectedPort=newValue;
            }

        });

        cmbBaudRate.valueProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable,
                                String oldValue, String newValue) {
                selectedRate=newValue;
            }

        });

        series = new XYChart.Series();
        line_Chart.getData().add(series);
        line_Chart.setAnimated(false);
        line_Chart.setCreateSymbols(false);
        line_Chart.getStyleClass().add("thick-chart");

        for(int i=0; i<NUM_OF_POINT; i++) {
            series.getData().add(new XYChart.Data(i, 0));

        }
    }

    public void connectArduino(String port, int baud){
        SerialPort serialPort = new SerialPort(port);
        try {
            serialPort.openPort();
            serialPort.setParams(baud,8,1,0);
            serialPort.setEventsMask(MASK_RXCHAR);
            serialPort.addEventListener((SerialPortEvent serialPortEvent) -> {
                if(serialPortEvent.isRXCHAR()){
                    try {

                        b = serialPort.readBytes();
                        int value = b[0] & 0xff;    //convert to int
                        String output = Integer.toString(value);
                        timestamp= new Date().getTime();

                        try {
                            wr.write(timestamp + "; " + output);
                            wr.write(System.lineSeparator());

                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        Platform.runLater(() -> {
                            shiftSeriesData((float)value);
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

    public void disconnectArduino(){

        if(arduinoPort != null){
            try {
                arduinoPort.removeEventListener();

                if(arduinoPort.isOpened()){
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

    public void shiftSeriesData(float newValue)
    {
        for(int i=0; i<NUM_OF_POINT-1; i++){
            XYChart.Data<Number, Number> ShiftDataUp = (XYChart.Data<Number, Number>)series.getData().get(i+1);
            Number shiftValue = ShiftDataUp.getYValue();
            XYChart.Data<Number, Number> ShiftDataDn = (XYChart.Data<Number, Number>)series.getData().get(i);
            ShiftDataDn.setYValue(shiftValue);
        }

        XYChart.Data<Number, Number> lastData = (XYChart.Data<Number, Number>)series.getData().get(NUM_OF_POINT-1);
        lastData.setYValue(newValue);

    }

}



