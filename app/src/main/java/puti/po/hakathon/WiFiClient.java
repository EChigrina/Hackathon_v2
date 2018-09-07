package puti.po.hakathon;

import android.content.Context;
import android.content.Intent;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class WiFiClient {
    private Socket socket;
    private Context UIContext;
    private String uri;

    public boolean connected;

    public WiFiClient(Context _UICOntext, String ip) {
        UIContext = _UICOntext;
        uri = "http://" + ip + ":7000";
        connected = false;
    }

    public void run() {
        try {
            socket = IO.socket(uri);
        }
        catch(URISyntaxException ex) {
            Log.e("SOCKETIO", ex.getMessage());
            return;
        }

        socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {

                //socket.disconnect();
            }

        }).on(Socket.EVENT_CONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                connected = true;
                Intent intent = new Intent("Connected");
                LocalBroadcastManager.getInstance(UIContext).sendBroadcast(intent);
            }

        }).on("newDriver", new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                Intent intent = new Intent("newDriver");
                intent.putExtra("driverLocation", args[0].toString());
                intent.putExtra("driverDestination", args[1].toString());
                LocalBroadcastManager.getInstance(UIContext).sendBroadcast(intent);
            }

        }).on("newPassenger", new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                Intent intent = new Intent("newPassenger");
                intent.putExtra("passengerLocation", args[0].toString());
                intent.putExtra("passengerDestination", args[1].toString());
                LocalBroadcastManager.getInstance(UIContext).sendBroadcast(intent);
            }

        }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {

            @Override
            public void call(Object... args) {
                connected = false;
            }

        });
        socket.connect();
    }

    public void addDriver(String gpsLocation, String destination) {
        socket.emit("addDriver", gpsLocation, destination);
    }

    public void addPassenger(String gpsLocation, String destination) {

        socket.emit("addPassenger", gpsLocation, destination);
    }

    public void getDrivers() {
        socket.emit("getDrivers");
    }

    public void getPassengers() {
        socket.emit("getPassengers");
    }
}
