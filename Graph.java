package com.example.rutedfsapp;

import java.util.*;

public class Graph {
    private Map<String, List<String>> adjacencyList = new HashMap<>();
    private List<String> path = new ArrayList<>();

    public void addEdge(String from, String to) {
        adjacencyList.putIfAbsent(from, new ArrayList<>());
        adjacencyList.putIfAbsent(to, new ArrayList<>());
        adjacencyList.get(from).add(to);
        adjacencyList.get(to).add(from); // Graf tidak berarah
    }

    public List<String> dfs(String start, String end) {
        path.clear();
        Set<String> visited = new HashSet<>();
        dfsRecursive(start, end, visited);
        return path;
    }

    private boolean dfsRecursive(String current, String end, Set<String> visited) {
        visited.add(current);
        path.add(current);

        if (current.equals(end)) return true;

        for (String neighbor : adjacencyList.getOrDefault(current, new ArrayList<>())) {
            if (!visited.contains(neighbor)) {
                if (dfsRecursive(neighbor, end, visited)) return true;
            }
        }

        path.remove(path.size() - 1);
        return false;
    }

    public List<String> bfs(String start, String end) {
        Map<String, String> parent = new HashMap<>();
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            String current = queue.poll();

            if (current.equals(end)) {
                return reconstructPath(parent, end);
            }

            for (String neighbor : adjacencyList.getOrDefault(current, new ArrayList<>())) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parent.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        return new ArrayList<>(); // Tidak ditemukan
    }

    private List<String> reconstructPath(Map<String, String> parent, String end) {
        List<String> path = new ArrayList<>();
        String current = end;
        while (current != null) {
            path.add(current);
            current = parent.get(current);
        }
        Collections.reverse(path);
        return path;
    }
}
