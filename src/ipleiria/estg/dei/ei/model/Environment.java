package ipleiria.estg.dei.ei.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import ipleiria.estg.dei.ei.model.geneticAlgorithm.Individual;
import ipleiria.estg.dei.ei.model.search.*;

import java.io.*;
import java.util.*;
import java.util.List;

public class Environment {

    private static Environment INSTANCE = new Environment();
    private Individual bestInRun;
    private HashMap<String, List<Node>> pairsMap;
    private ArrayList<EnvironmentListener> listeners;
    private HashMap<Integer, List<Node>> warehouseGraph;
    private int graphSize;
    private int edgesSize;
    private HashMap<Integer, List<Node>> picksGraph;
    private HashMap<Integer, Node> nodes;
    private List<Node> decisionNodes;
    private List<Integer> picks;
    private List<Integer> agents;
    private int offloadArea;
    private HashMap<String, Edge> edgesMap;
    private List<Edge> edges;
    private int maxLine;
    private int maxColumn;
    private int timeWeight;
    private int collisionsWeight;

    private Environment() {
        this.listeners = new ArrayList<>();
        this.pairsMap = new HashMap<>();
    }

    public static Environment getInstance() {
        return INSTANCE;
    }

    public void readInitialStateFromFile(File file) {
        this.nodes = new HashMap<>();
        this.graphSize = 0;
        this.edgesSize = 0;
        this.warehouseGraph = new HashMap<>();
        this.edgesMap = new HashMap<>();
        this.edges = new ArrayList<>();
        this.agents = new ArrayList<>();
        this.decisionNodes = new ArrayList<>();

        try {
            JsonObject jsonObject = JsonParser.parseReader(new FileReader(file)).getAsJsonObject();

            // IMPORT NODES
            JsonArray jsonNodes = jsonObject.getAsJsonArray("nodes");

            JsonObject jsonNode;
            Node decisionNode;
            for (JsonElement elementNode : jsonNodes) {
                jsonNode = elementNode.getAsJsonObject();
                decisionNode = new Node(jsonNode.get("nodeNumber").getAsInt(), jsonNode.get("line").getAsInt(), jsonNode.get("column").getAsInt(), jsonNode.get("type").getAsString());
                this.nodes.put(jsonNode.get("nodeNumber").getAsInt(), decisionNode);
                this.decisionNodes.add(decisionNode);
                this.graphSize++;
            }

            // IMPORT SUCCESSORS
            JsonArray jsonSuccessors;
            JsonObject jsonSuccessor;
            Node node;
            for (JsonElement elementNode : jsonNodes) {
                jsonNode = elementNode.getAsJsonObject();
                List<Node> successors = new ArrayList<>();
                this.warehouseGraph.put(jsonNode.get("nodeNumber").getAsInt(), successors);

                jsonSuccessors = jsonNode.getAsJsonArray("successors");
                for (JsonElement elementSuccessor : jsonSuccessors) {
                    jsonSuccessor = elementSuccessor.getAsJsonObject();
                    node = new Node(this.nodes.get(jsonSuccessor.get("nodeNumber").getAsInt()));
                    node.setCostFromAdjacentNode(jsonSuccessor.get("distance").getAsDouble());

                    successors.add(node);
                }
            }

            // IMPORT EDGES
            JsonArray jsonEdges = jsonObject.getAsJsonArray("edges");

            JsonObject jsonEdge;
            Edge edge;
            Node node1;
            Node node2;
            for (JsonElement elementEdge : jsonEdges) {
                jsonEdge = elementEdge.getAsJsonObject();
                node1 = this.nodes.get(jsonEdge.get("node1Number").getAsInt());
                node2 = this.nodes.get(jsonEdge.get("node2Number").getAsInt());
                node1.addEdge(jsonEdge.get("edgeNumber").getAsInt());
                node2.addEdge(jsonEdge.get("edgeNumber").getAsInt());
                edge = new Edge(jsonEdge.get("edgeNumber").getAsInt(), node1, node2, jsonEdge.get("distance").getAsDouble(), jsonEdge.get("direction").getAsInt());

                this.edges.add(edge);
                this.edgesMap.put(jsonEdge.get("node1Number") +  "-" + jsonEdge.get("node2Number"), edge);
                this.edgesSize++;
            }
            Collections.sort(this.edges);

            // IMPORT AGENTS
            JsonArray jsonAgents = jsonObject.getAsJsonArray("agents");

            JsonObject jsonAgent;
            int nodeNumber;
            for (JsonElement elementAgent : jsonAgents) {
                jsonAgent = elementAgent.getAsJsonObject();

                nodeNumber = addNodeToGraph(this.warehouseGraph, jsonAgent.get("edgeNumber").getAsInt(), jsonAgent.get("line").getAsInt(), jsonAgent.get("column").getAsInt());

                if (nodeNumber != 0) {
                    this.agents.add(nodeNumber);
                }
            }

            // IMPORT OFFLOAD
            this.offloadArea = jsonObject.get("offloadArea").getAsInt();

        } catch (Exception e) {
            e.printStackTrace();
        }

        fireCreateEnvironment();
    }

    // RETURNS THE NUMBER OF THE NODE ADDED TO THE GRAPH  OR 0 IF UNSUCCESSFUL // TODO COULD THROW EXCEPTION INSTEAD OF RETURNING 0
    private int addNodeToGraph(HashMap<Integer, List<Node>> graph, int edgeNumber, int line, int column) {
        Node edgeNode1 = this.edges.get(edgeNumber - 1).getNode1();
        Node edgeNode2 = this.edges.get(edgeNumber - 1).getNode2();

        if (edgeNode1.getLine() == line) {
            return edgeNode1.getNodeNumber();
        }

        Node previousNode;
        Node nextNode = edgeNode1;
        do {
            previousNode = nextNode;
            nextNode = null;
            for (Node n : graph.get(previousNode.getNodeNumber())) {
                if (n.belongsToEdge(edgeNumber)) {
                    nextNode = n;
                    break;
                }
            }

            if (nextNode == null) {
                return 0;
            }

            if ((line - previousNode.getLine()) * (line - nextNode.getLine()) < 0) {
                // ADD NEW NODE TO NODES
                this.graphSize++;
                Node newNode = new Node(this.graphSize, line, column, "O");
                this.nodes.put(newNode.getNodeNumber(), newNode);

                // ADD NEW NODE'S SUCCESSORS TO GRAPH
                List<Node> successors = new ArrayList<>();
                graph.put(newNode.getNodeNumber(), successors);
                Node n1 = new Node(previousNode);
                n1.setCostFromAdjacentNode(Math.abs(previousNode.getLine() - line));
                Node n2 = new Node(nextNode);
                n2.setCostFromAdjacentNode(Math.abs(nextNode.getLine() - line));
                successors.add(n1);
                successors.add(n2);

                // ALTER PREVIOUS AND NEXT NODE SUCCESSORS
                Node finalNextNode = nextNode;
                graph.get(previousNode.getNodeNumber()).removeIf(n -> n.getNodeNumber() == finalNextNode.getNodeNumber());
                Node finalPreviousNode = previousNode;
                graph.get(nextNode.getNodeNumber()).removeIf(n -> n.getNodeNumber() == finalPreviousNode.getNodeNumber());

                n1 = new Node(newNode);
                n1.setCostFromAdjacentNode(Math.abs(previousNode.getLine() - line));
                n1.addEdge(edgeNumber);
                n2 = new Node(newNode);
                n2.setCostFromAdjacentNode(Math.abs(nextNode.getLine() - line));
                n2.addEdge(edgeNumber);

                graph.get(previousNode.getNodeNumber()).add(n1);
                graph.get(nextNode.getNodeNumber()).add(n2);

                // CREATE NEW SUB EDGES
                this.edgesMap.put(previousNode.getNodeNumber() + "-" + newNode.getNodeNumber(), new Edge(++this.edgesSize, this.nodes.get(previousNode.getNodeNumber()), newNode, Math.abs(previousNode.getLine() - line), this.edges.get(edgeNumber - 1).getDirection()));
                this.edgesMap.put(nextNode.getNodeNumber() + "-" + newNode.getNodeNumber(), new Edge(++this.edgesSize, this.nodes.get(nextNode.getNodeNumber()), newNode, Math.abs(nextNode.getLine() - line), this.edges.get(edgeNumber - 1).getDirection()));

                return newNode.getNodeNumber();
            }

            if (line == nextNode.getLine()) {
                return nextNode.getNodeNumber();
            }

        } while (nextNode.getNodeNumber() != edgeNode2.getNodeNumber());

        return 0;
    }

//    public void loadPicksFromFile(File file) throws IOException {
//        this.picks = new ArrayList<>();
//
//        BufferedReader csvReader = new BufferedReader(new FileReader(file));
//        String row = "";
//        while ((row = csvReader.readLine()) != null) {
//            String[] data = row.split(";");
//            this.picks.add(Integer.parseInt(data[1]));
//        }
//        csvReader.close();
//
//        createPicksGraph();
//    }

//    private void createPicksGraph() {
//        this.picksGraph = new HashMap<>();
//
//        for (int i = 1; i <= this.graphSize; i++) {
//            List<Node> successors = new LinkedList<>();
//            this.picksGraph.put(i, successors);
//
//            for (Node node : this.originalGraph.get(i)) {
//                successors.add(new Node(node));
//            }
//        }
//
//
//        for (int i = 1; i <= this.graphSize ; i++) {
//            if (this.nodes.get(i).getType().equals("D")) {
//                continue;
//            }
//
//            if (this.picks.contains(i)) {
//                continue;
//            }
//
//            if (i == this.offloadArea) {
//                continue;
//            }
//
//            List<Node> successors = this.picksGraph.get(i);
//            for (Node node : successors) {
//                List<Node> nodeSuccessors = this.picksGraph.get(node.getNodeNumber());
//                double cost = node.getCostFromAdjacentNode();
//                int finalI = i;
//                nodeSuccessors.removeIf(n -> n.getNodeNumber() == finalI);
//
//                for (Node n : successors) {
//                    if (n.getNodeNumber() != node.getNodeNumber()) {
//                        Node nodeToAdd = new Node(n);
//                        nodeToAdd.addCost(cost);
//                        nodeSuccessors.add(nodeToAdd);
//                    }
//                }
//            }
//
//            this.picksGraph.remove(i);
//        }
//
//        for (int i = 1; i < this.graphSize + 1; i++) {
//            System.out.println(i + "->" + this.picksGraph.get(i));
//        }
//        System.out.println("----------------------------------------------------");
//
//        fireCreateSimulation();
//    }

    public void executeSolution() throws InterruptedException {
        int[] genome = bestInRun.getGenome();
        List<List<Node>> agentsPathNodes = separateGenomeByAgents(genome);
        List<List<Location>> solutionLocations = computeSolutionLocations(agentsPathNodes);

        int numIterations = 0;
        for (List<Location> l : solutionLocations) {
            if (l.size() > numIterations) {
                numIterations = l.size();
            }
        }

        fireCreateSimulationPicks();

        Node offloadNode = this.nodes.get(offloadArea);
        Location offloadLocation = new Location(offloadNode.getLine(), offloadNode.getColumn());
        List<Location> iterationAgentsLocations;
        for (int i = 0; i < numIterations; i++) {
            iterationAgentsLocations = new LinkedList<>();
            for (List<Location> l : solutionLocations) {
                if (i < l.size()) {
                    iterationAgentsLocations.add(l.get(i));
                } else {
                    iterationAgentsLocations.add(offloadLocation);
                }
            }
            Thread.sleep(500);
            fireUpdateEnvironment(iterationAgentsLocations);
        }
    }

    private List<List<Location>> computeSolutionLocations(List<List<Node>> agentsPathNodes) {
        List<List<Location>> solutionLocations = new ArrayList<>();
        List<Location> agentLocations = new ArrayList<>();

        Node n1;
        Node n2;
        int action;
        int line;
        int column;
        for (List<Node> l : agentsPathNodes) {
            for (int i = 0; i < l.size() - 1; i++) {
                n1 = this.nodes.get(l.get(i).getNodeNumber());
                n2 = this.nodes.get(l.get(i + 1).getNodeNumber());
                line = n1.getLine();
                column = n1.getColumn();


                if (n1.getColumn() < n2.getColumn() || n1.getLine() < n2.getLine()) {
                    action = 1;
                } else {
                    action = -1;
                }

                if (n1.getLine() == n2.getLine()) {

                    do {
                        column += action;
                        agentLocations.add(new Location(line, column));
                    } while (column != n2.getColumn());

                } else {

                    do {
                        line += action;
                        agentLocations.add(new Location(line, column));
                    } while (line != n2.getLine());

                }
            }
            solutionLocations.add(agentLocations);
            agentLocations = new ArrayList<>();
        }

        return solutionLocations;
    }

    private List<List<Node>> separateGenomeByAgents(int[] genome) {
        List<List<Node>> agents = new ArrayList<>();
        List<Node> agentPicks = new ArrayList<>();
        int agent = 0;

        List<List<Node>> agentsPaths = new ArrayList<>();
        List<Node> agentPath = new ArrayList<>();

        agentPicks.add(this.nodes.get(this.agents.get(agent)));
        for (int value : genome) {
            if (value < 0) {
                agentPicks.add(this.nodes.get(this.offloadArea));
                agents.add(agentPicks);
                agentPicks = new ArrayList<>();
                agentPicks.add(this.nodes.get(this.agents.get(++agent)));
                continue;
            }
            agentPicks.add(this.nodes.get(picks.get(value - 1)));
        }
        agentPicks.add(this.nodes.get(this.offloadArea));
        agents.add(agentPicks);

        agent = 0;

        for (List<Node> l : agents) {
            agentPath.add(this.nodes.get(this.agents.get(agent++)));
            for (int i = 0; i < l.size() - 1; i++) {
                agentPath.addAll(this.pairsMap.get(l.get(i).getNodeNumber() + "-" + l.get(i + 1).getNodeNumber()));
            }
            agentsPaths.add(agentPath);
            agentPath = new ArrayList<>();
        }

        return agentsPaths;
    }

    public int getTimeWeight() {
        return timeWeight;
    }

    public void setTimeWeight(int timeWeight) {
        this.timeWeight = timeWeight;
    }

    public int getCollisionsWeight() {
        return collisionsWeight;
    }

    public void setCollisionsWeight(int collisionsWeight) {
        this.collisionsWeight = collisionsWeight;
    }

    public List<Integer> getPicks() {
        return picks;
    }

    public List<Location> getPickNodes() {
        List<Location> picks = new LinkedList<>();
        for (int i : this.picks) {
            Node node = this.nodes.get(i);
            picks.add(new Location(node.getLine(), node.getColumn()));
        }

        return picks;
    }

    public List<Location> getAgentNodes() {
        List<Location> agents = new ArrayList<>();
        for (int i : this.agents) {
            Node node = this.nodes.get(i);
            agents.add(new Location(node.getLine(), node.getColumn()));
        }

        return agents;
    }

    public Node getNode(int i) {
        return nodes.get(i);
    }

    public List<Node> getAdjacentNodes(int nodeNumber) {
        return picksGraph.get(nodeNumber);
    }

    public int getNumberOfAgents() {
        return agents.size();
    }

    public int getNumberOfPicks() {
        return picks.size();
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public int getMaxLine() {
        return maxLine;
    }

    public int getMaxColumn() {
        return maxColumn;
    }

    public List<Node> getDecisionNodes() {
        return decisionNodes;
    }

    public void setBestInRun(Individual bestInRun) {
        this.bestInRun = bestInRun;
    }

    public HashMap<String, List<Node>> getPairsMap() {
        return pairsMap;
    }

    public boolean pairsMapContains(Node firstNode, Node secondNode) {
        return this.pairsMap.containsKey(firstNode.getNodeNumber() + "-" + secondNode.getNodeNumber());
    }

    public void addToPairsMap(List<Node> path) {
        List<Node> invPath = createInversePath(path);

        this.pairsMap.put(path.get(0).getNodeNumber() + "-" + path.get(path.size() - 1).getNodeNumber(), path);
        this.pairsMap.put(invPath.get(0).getNodeNumber() + "-" + invPath.get(path.size() - 1).getNodeNumber(), invPath);

        path.remove(0);
        invPath.remove(0);
    }

    private List<Node> createInversePath(List<Node> path) {
        List<Node> invPath = new ArrayList<>();
        path.forEach((node) -> invPath.add(new Node(node)));

        double pathSize = path.get(path.size() - 1).getG();
        for (int i = path.size() - 1; i >= 0; i--) {
            invPath.get(i).setG(pathSize - path.get(i).getG());
        }

        Collections.reverse(invPath);

        return invPath;
    }

    public List<Node> getPath(Node firstNode, Node secondNode) {
        return this.pairsMap.get(firstNode.getNodeNumber() + "-" + secondNode.getNodeNumber());
    }

    public List<Integer> getAgents() {
        return agents;
    }

    public int getOffloadArea() {
        return offloadArea;
    }

    public Node getOffloadAreaNode() {
        return this.nodes.get(this.offloadArea);
    }

    public synchronized void addEnvironmentListener(EnvironmentListener l) {
        if (!listeners.contains(l)) {
            listeners.add(l);
        }
    }

//    public synchronized void removeEnvironmentListener(EnvironmentListener l) {
//        listeners.remove(l);
//    }

    public void fireUpdateEnvironment(List<Location> agents) {
        for (EnvironmentListener listener : listeners) {
            listener.updateEnvironment(agents);
        }
    }

    public void fireCreateEnvironment() {
        for (EnvironmentListener listener : listeners) {
            listener.createEnvironment();
        }
    }

    public void fireCreateSimulation() {
        for (EnvironmentListener listener : listeners) {
            listener.createSimulation();
        }
    }

    public void fireCreateSimulationPicks() {
        for (EnvironmentListener listener : listeners) {
            listener.createSimulationPicks();
        }
    }
}
