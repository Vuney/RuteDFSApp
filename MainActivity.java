package com.example.rutedfsapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.graphics.Color;
import android.text.TextUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.*;
import com.google.android.gms.maps.model.*;

import org.json.JSONArray;
import org.json.JSONObject;

import android.content.pm.PackageManager;

import java.util.*;

public class MainActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private final Map<String, LatLng> coordinates = new HashMap<>();
    private Spinner spinnerStart, spinnerEnd, spinnerAlgorithm;
    private Button btnFindRoute, btnResetMap, btnSwitchMode, btnNavigate;
    private TextView txtRouteInfo;
    private Graph graph = new Graph();
    private LatLng userLocation = null;
    private boolean isTestMode = false;
    private LatLng lastDestination;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        spinnerStart = findViewById(R.id.spinnerStart);
        spinnerEnd = findViewById(R.id.spinnerEnd);
        spinnerAlgorithm = findViewById(R.id.spinnerAlgorithm);
        btnFindRoute = findViewById(R.id.btnFindRoute);
        btnResetMap = findViewById(R.id.btnResetMap);
        btnSwitchMode = findViewById(R.id.btnSwitchMode);
        btnNavigate = findViewById(R.id.btnNavigate);
        txtRouteInfo = findViewById(R.id.txtRouteInfo);

        setupCoordinates();
        setupGraph();
        refreshSpinners();

        ArrayAdapter<String> algoAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, new String[]{"DFS", "BFS"});
        algoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAlgorithm.setAdapter(algoAdapter);

        btnFindRoute.setOnClickListener(v -> {
            String start = spinnerStart.getSelectedItem().toString();
            String end = spinnerEnd.getSelectedItem().toString();
            String algorithm = spinnerAlgorithm.getSelectedItem().toString();

            if (start.equals(end)) {
                Toast.makeText(this, "Lokasi awal dan tujuan tidak boleh sama.", Toast.LENGTH_SHORT).show();
                return;
            }
            if (start.equals("Lokasi Saya") && userLocation == null) {
                Toast.makeText(this, "Tunggu hingga lokasi Anda terdeteksi.", Toast.LENGTH_SHORT).show();
                return;
            }

            String actualStart = start.equals("Lokasi Saya") ? nearestNodeFromUser() : start;

            if (mMap != null) mMap.clear();

            List<String> route = algorithm.equals("BFS") ? graph.bfs(actualStart, end) : graph.dfs(actualStart, end);

            if (route != null && !route.isEmpty()) {
                PolylineOptions polylineOptions = new PolylineOptions().width(10).color(Color.RED);

                for (String pointName : route) {
                    LatLng latLng = coordinates.get(pointName);
                    if (latLng != null) {
                        if (pointName.equals(actualStart)) {
                            mMap.addMarker(new MarkerOptions().position(latLng)
                                    .title("Start: " + pointName)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
                        } else if (pointName.equals(end)) {
                            mMap.addMarker(new MarkerOptions().position(latLng)
                                    .title("End: " + pointName)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
                        } else {
                            mMap.addMarker(new MarkerOptions().position(latLng).title(pointName));
                        }
                        polylineOptions.add(latLng);
                    }
                }

                mMap.addPolyline(polylineOptions);
                mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(coordinates.get(actualStart), 13));

                double totalDistance = 0;
                for (int i = 0; i < route.size() - 1; i++) {
                    LatLng from = coordinates.get(route.get(i));
                    LatLng to = coordinates.get(route.get(i + 1));
                    if (from != null && to != null) {
                        totalDistance += calculateDistance(from, to);
                    }
                }

                // Estimasi biaya
                int tarifPerKm = 1000;
                int estimasiBiaya = (int) (totalDistance * tarifPerKm);

                txtRouteInfo.setText("Rute (" + algorithm + "): " + TextUtils.join(" → ", route) +
                        "\nTotal jarak: " + String.format("%.2f", totalDistance) + " km" +
                        "\nEstimasi biaya: Rp" + estimasiBiaya);

                int waktuPerKm = 3;
                int estimasiWaktu = (int) (totalDistance * waktuPerKm);

// Mengonversi estimasi waktu ke dalam jam dan menit
                int jam = estimasiWaktu / 60;
                int menit = estimasiWaktu % 60;

                String waktuFormatted;
                if (jam > 0) {
                    waktuFormatted = jam + " jam " + menit + " menit";
                } else {
                    waktuFormatted = menit + " menit";
                }

                txtRouteInfo.setText("Rute (" + algorithm + "): " + TextUtils.join(" → ", route) +
                        "\nTotal jarak: " + String.format("%.2f", totalDistance) + " km" +
                        "\nEstimasi waktu: " + waktuFormatted);



                LatLng origin = coordinates.get(actualStart);
                LatLng destination = coordinates.get(end);
                if (origin != null && destination != null) {
                    drawRouteUsingDirectionsAPI(origin, destination);
                    lastDestination = destination;
                }
            }
        });

        btnResetMap.setOnClickListener(v -> {
            if (mMap != null) {
                mMap.clear();
                txtRouteInfo.setText("Rute telah direset.");
                Toast.makeText(this, "Peta direset.", Toast.LENGTH_SHORT).show();
            }
        });

        btnSwitchMode.setOnClickListener(v -> {
            isTestMode = !isTestMode;
            setupCoordinates();
            setupGraph();
            refreshSpinners();
            txtRouteInfo.setText("");
            if (mMap != null) mMap.clear();
            Toast.makeText(this, isTestMode ? "Mode Uji DFS/BFS Aktif" : "Mode Angkot Aktif", Toast.LENGTH_SHORT).show();
            btnSwitchMode.setText("Mode: " + (isTestMode ? "Uji DFS/BFS" : "Angkot"));
        });

        btnNavigate.setOnClickListener(v -> {
            if (lastDestination != null) {
                String uri = "google.navigation:q=" + lastDestination.latitude + "," + lastDestination.longitude;
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                intent.setPackage("com.google.android.apps.maps");
                startActivity(intent);
            } else {
                Toast.makeText(this, "Tujuan belum tersedia.", Toast.LENGTH_SHORT).show();
            }
        });

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 1);
            return;
        }
        mMap.setMyLocationEnabled(true);
        mMap.setOnMyLocationChangeListener(location ->
                userLocation = new LatLng(location.getLatitude(), location.getLongitude()));
    }

    private void setupCoordinates() {
        coordinates.clear();
        if (isTestMode) {
            coordinates.put("A", new LatLng(-5.10, 119.40));
            coordinates.put("B", new LatLng(-5.11, 119.41));
            coordinates.put("C", new LatLng(-5.11, 119.39));
            coordinates.put("D", new LatLng(-5.12, 119.42));
            coordinates.put("E", new LatLng(-5.12, 119.38));
            coordinates.put("F", new LatLng(-5.13, 119.38));
        } else {
            coordinates.put("Daya", new LatLng(-5.1357, 119.5000));
            coordinates.put("MTC", new LatLng(-5.1520, 119.4123));
            coordinates.put("Karebosi", new LatLng(-5.1353, 119.4131));
            coordinates.put("Panakkukang", new LatLng(-5.1442, 119.4323));
            coordinates.put("Unhas", new LatLng(-5.1314, 119.4876));
            coordinates.put("Bandara", new LatLng(-5.0615, 119.5541));
        }
    }

    private void setupGraph() {
        graph = new Graph();
        if (isTestMode) {
            graph.addEdge("A", "B");
            graph.addEdge("A", "C");
            graph.addEdge("B", "D");
            graph.addEdge("C", "E");
            graph.addEdge("E", "F");
        } else {
            graph.addEdge("Daya", "MTC");
            graph.addEdge("MTC", "Karebosi");
            graph.addEdge("Karebosi", "Panakkukang");
            graph.addEdge("Panakkukang", "Unhas");
            graph.addEdge("Unhas", "Bandara");
        }
    }

    private void refreshSpinners() {
        List<String> locations = new ArrayList<>(coordinates.keySet());
        locations.add(0, "Lokasi Saya");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, locations);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStart.setAdapter(adapter);
        spinnerEnd.setAdapter(adapter);
    }

    private double calculateDistance(LatLng point1, LatLng point2) {
        double earthRadius = 6371;
        double dLat = Math.toRadians(point2.latitude - point1.latitude);
        double dLng = Math.toRadians(point2.longitude - point1.longitude);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(point1.latitude)) *
                        Math.cos(Math.toRadians(point2.latitude)) *
                        Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private String nearestNodeFromUser() {
        String nearest = null;
        double minDist = Double.MAX_VALUE;
        for (Map.Entry<String, LatLng> entry : coordinates.entrySet()) {
            double dist = calculateDistance(userLocation, entry.getValue());
            if (dist < minDist) {
                minDist = dist;
                nearest = entry.getKey();
            }
        }
        return nearest;
    }

    private void drawRouteUsingDirectionsAPI(LatLng origin, LatLng destination) {
        String apiKey = "AIzaSyBfEz8ClpGYcEd6Aj-OODbEKn7wHy--Xc4"; // Ganti dengan API key Anda
        String url = "https://maps.googleapis.com/maps/api/directions/json?" +
                "origin=" + origin.latitude + "," + origin.longitude +
                "&destination=" + destination.latitude + "," + destination.longitude +
                "&key=" + apiKey;

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    try {
                        JSONArray routes = response.getJSONArray("routes");
                        if (routes.length() > 0) {
                            JSONObject route = routes.getJSONObject(0);
                            JSONObject overviewPolyline = route.getJSONObject("overview_polyline");
                            String points = overviewPolyline.getString("points");
                            List<LatLng> decodedPoints = decodePolyline(points);

                            JSONArray legs = route.getJSONArray("legs");
                            JSONObject leg = legs.getJSONObject(0);
                            String durationText = leg.getJSONObject("duration").getString("text");

                            mMap.addPolyline(new PolylineOptions()
                                    .addAll(decodedPoints)
                                    .color(Color.BLUE)
                                    .width(10));

                            txtRouteInfo.append("\nEstimasi waktu tempuh: " + durationText);
                        }
                    } catch (Exception e) {
                        Toast.makeText(this, "Gagal memproses data dari Directions API.", Toast.LENGTH_SHORT).show();
                        e.printStackTrace();
                    }
                },
                error -> Toast.makeText(this, "Gagal menghubungi Google Directions API.", Toast.LENGTH_SHORT).show()
        );
        Volley.newRequestQueue(this).add(request);
    }

    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;
        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            poly.add(new LatLng(lat / 1E5, lng / 1E5));
        }
        return poly;
    }

    private static class Graph {
        private final Map<String, List<String>> adjList = new HashMap<>();

        void addEdge(String from, String to) {
            adjList.computeIfAbsent(from, k -> new ArrayList<>()).add(to);
            adjList.computeIfAbsent(to, k -> new ArrayList<>()).add(from);
        }

        List<String> bfs(String start, String goal) {
            List<String> path = new ArrayList<>();
            Map<String, String> parent = new HashMap<>();
            Queue<String> queue = new LinkedList<>();

            queue.add(start);
            parent.put(start, null);

            while (!queue.isEmpty()) {
                String current = queue.poll();
                if (current.equals(goal)) break;
                for (String neighbor : adjList.getOrDefault(current, new ArrayList<>())) {
                    if (!parent.containsKey(neighbor)) {
                        parent.put(neighbor, current);
                        queue.add(neighbor);
                    }
                }
            }

            if (!parent.containsKey(goal)) return null;
            for (String at = goal; at != null; at = parent.get(at)) {
                path.add(0, at);
            }
            return path;
        }

        List<String> dfs(String start, String goal) {
            List<String> path = new ArrayList<>();
            Map<String, String> parent = new HashMap<>();
            Stack<String> stack = new Stack<>();

            stack.push(start);
            parent.put(start, null);

            while (!stack.isEmpty()) {
                String current = stack.pop();
                if (current.equals(goal)) break;
                for (String neighbor : adjList.getOrDefault(current, new ArrayList<>())) {
                    if (!parent.containsKey(neighbor)) {
                        parent.put(neighbor, current);
                        stack.push(neighbor);
                    }
                }
            }

            if (!parent.containsKey(goal)) return null;
            for (String at = goal; at != null; at = parent.get(at)) {
                path.add(0, at);
            }
            return path;
        }
    }
}
