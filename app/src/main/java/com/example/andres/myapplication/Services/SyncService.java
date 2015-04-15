package com.example.andres.myapplication.Services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;

public class SyncService extends Service {

    public static final int STUDENT_GETTED = 1;
    public static final int MSG_REGISTER_CLIENT = 2;
    public static final int SYNC_REQUESTED = 3;
    public static final int UPGRADE_STUDENT = 4;

    String jsonStringStudents;

    private final IBinder mBinder = new LocalBinder();
    Messenger client;

    private final Messenger mMessenger = new Messenger(new IncomingMessageHandler()); // Target we publish for clients to send messages to IncomingHandler.


    public SyncService() {

    }

    /**
     * Clase usada por el cliente Binder.
     */
    public class LocalBinder extends Binder {
        public SyncService getService(){
            // Returns the SyncService isntance. With this clients can call the public methods.
            return SyncService.this;
        }
    }





    @Override
    public IBinder onBind(Intent intent) {

        return mMessenger.getBinder();
    }


    protected void updateDB(){

    }

    /**
     * Get JSON with students from cloud
     */
    public void syncStudents() throws JSONException, ExecutionException, InterruptedException {

        (new RequestTask("GET", null)).execute("http://radiant-savannah-9544.herokuapp.com/students.json");

    }

    /**
     * Add student to the web app DB if not added
     */
    public void addStudents(){
        (new RequestTask("POST", jsonStringStudents)).execute("http://radiant-savannah-9544.herokuapp.com/students.json");

    }

    protected void processJsonResponse(ArrayList<String> responses, String modo) throws JSONException {


        if (modo.compareTo("POST")==0){
            if (responses == null) return;
            for (int i=0; i<responses.size(); i++) {
                JSONObject jsonArray = new JSONObject(responses.get(i).toString());
                int idCloud = jsonArray.getInt("id");
                String names = jsonArray.getString("name");
                String fln = jsonArray.getString("first_lastname");
                String sln = jsonArray.getString("second_lastname");

                upgradeStudent(names, fln, sln, idCloud);
            }

        }
        else{
            if (responses.size()>0){
                String response = responses.get(0);
                // mandar response a activity y luego a fragment
                sendStudentsToUI(response);
            }
            else Toast.makeText(getApplicationContext(), "No internet connection", Toast.LENGTH_SHORT).show();


        }
    }
    /**
     * Handle incoming messages from MainActivity
     */
    private class IncomingMessageHandler extends Handler { // Handler of incoming messages from clients.
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    client = msg.replyTo;
                    break;
                case SYNC_REQUESTED:
                    try {
                        jsonStringStudents = msg.getData().getString("jsonstring");
                        syncStudents();

                    } catch (JSONException e) {
                        e.printStackTrace();
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    break;

                default:
                    super.handleMessage(msg);
            }
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }


    private void sendStudentsToUI(String students) {
            try {

                // Send data as a String
                Bundle bundle = new Bundle();
                bundle.putString("students",students);
                Message msg = Message.obtain(null, STUDENT_GETTED);
                msg.setData(bundle);
                client.send(msg);

            } catch (RemoteException e) {
                // The client is dead. Remove it from the list.
                client = null;
            }
    }

    private void upgradeStudent(String names, String fln, String sln, int idCloud){
        try {

            // Send data as a String
            Bundle bundle = new Bundle();

            bundle.putString("names", names);
            bundle.putString("firstlastname", fln);
            bundle.putString("secondlastname", sln);
            bundle.putInt("idcloud", idCloud);

            Message msg = Message.obtain(null, UPGRADE_STUDENT);
            msg.setData(bundle);
            client.send(msg);

        } catch (RemoteException e) {
            // The client is dead. Remove it from the list.
            client = null;
        }
    }



    private class RequestTask extends AsyncTask<String, Void, ArrayList<String>> {
        private String modo;
        String students;
        ArrayList<String> responses;


        public RequestTask(String modo, String students){
            this.modo = modo;
            this.students = students;
        }


        @Override
        protected ArrayList<String> doInBackground(String... params) {
            try {
                responses = performRequest(params[0]);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return responses;
        }

        @Override
        protected void onPostExecute(ArrayList<String> result) {
            super.onPostExecute(result);
            try {
                if (modo.equals("GET")){
                    addStudents();
                }
                processJsonResponse(result, modo);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        public ArrayList<String> performRequest(String urlString) throws IOException {

            URL url;
            HttpURLConnection urlConnection = null;
            ArrayList<String> responses = new ArrayList<String>();
            if (!isNetworkAvailable()){
                return responses;
            }
            try {
                url = new URL(urlString);


                if (modo.compareTo("GET") == 0){

                    urlConnection = (HttpURLConnection) url.openConnection();
                    urlConnection.setRequestMethod("GET");

                    InputStream is;

                    int responseCode = urlConnection.getResponseCode();

                    is = urlConnection.getInputStream();

                    InputStreamReader isr = new InputStreamReader(is);


                    BufferedReader reader = new BufferedReader(isr);

                    String response = "";

                    String line;


                    while ((line = reader.readLine()) != null) {
                            response += line + "\n";
                    }
                    reader.close();
                    urlConnection.disconnect();

                    responses.add(response);

                    return responses;
                }
                else if (modo.compareTo("POST") == 0){
                    JSONArray jsonArray = new JSONArray(students);

                    for (int i = 0; i< jsonArray.length(); i++){
                        JSONObject object = jsonArray.getJSONObject(i);

                        urlConnection = (HttpURLConnection) url.openConnection();
                        urlConnection.setDoOutput(true);
                        urlConnection.setDoInput(true);
                        urlConnection.setRequestProperty("Content-Type", "application/json");
                        urlConnection.setRequestProperty("Accept", "application/json");
                        urlConnection.setRequestMethod("POST");

                        OutputStreamWriter wr= new OutputStreamWriter(urlConnection.getOutputStream());
                        wr.write(object.toString());
                        wr.flush();

                        int responsecode = urlConnection.getResponseCode();

                        if (responsecode==201){
                            BufferedReader br = new BufferedReader(new InputStreamReader((urlConnection.getInputStream())));
                            String output= "";
                            String a;
                            while ((a = br.readLine()) != null) {
                                output += a;
                            }

                            responses.add(output);
                        }



                    }
                    return responses;
                }

            }

             catch (UnknownHostException e) {

                Toast.makeText(getApplicationContext(), "No internet connection", Toast.LENGTH_SHORT).show();

            } catch (Exception ex) {

                Toast.makeText(getApplicationContext(), "Error", Toast.LENGTH_SHORT).show();

            } finally {
                if (urlConnection != null)  urlConnection.disconnect();
            }

            return responses;

        }



    }
}
