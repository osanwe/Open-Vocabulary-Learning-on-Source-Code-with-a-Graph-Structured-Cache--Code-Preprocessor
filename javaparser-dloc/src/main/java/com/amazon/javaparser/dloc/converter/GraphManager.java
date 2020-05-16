// Copyright 2018 Amazon.com, Inc. or its affiliates. All Rights Reserved.
package com.amazon.javaparser.dloc.converter;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.OtherLinkType;
import com.github.javaparser.ast.expr.SimpleName;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.set.ListOrderedSet;
import org.apache.commons.lang.StringUtils;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerEdge;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerGraph;
import org.apache.tinkerpop.gremlin.tinkergraph.structure.TinkerVertex;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GraphManager {

    private TinkerGraph graph;
    private int id = 1;
    private GraphAttributeParser graphAttributeParser = new GraphAttributeParser();
    private Map<Integer, TinkerVertex> vertexCache;

    public GraphManager() {
        graph = TinkerGraph.open();
        vertexCache = new HashMap<>();
    }

    public void process(Node node) {
        List<Node> nodeList = collectNode(node);
        createAndCacheVertex(nodeList);
        processEdge(node);
    }

    public TinkerGraph getGraph() {
        return this.graph;
    }

    private void processEdge(Node node) {
        TinkerVertex from = vertexCache.get(node.hashCode(true));
        for (Map.Entry<OtherLinkType, ListOrderedSet<Node>> entry : node.getOthersNodes().entrySet()) {
            if (entry.getKey() == OtherLinkType.TYPE) {
                List<String> referenceList = entry.getValue().asList().stream().map(n -> {
                    if (n instanceof SimpleName) {
                        return ((SimpleName) n).getIdentifier();
                    }
                    throw new RuntimeException("Not expected.");
                }).collect(Collectors.toList());
                setReference(from, referenceList);
            } else {
                for (Node child : entry.getValue().asList()) {
                    TinkerVertex to = vertexCache.get(child.hashCode(true));
                    if (to == null) {
                        throw new RuntimeException("Not expected.");
                    }
                    TinkerEdge edge = (TinkerEdge) from.addEdge(entry.getKey().name(), to);
                    edge.property("type", entry.getKey().name());
                    if (entry.getKey() == OtherLinkType.AST) {
                        processEdge(child);
                    }
                }
            }
        }
    }

    private List<Node> collectNode(Node node) {
        List<Node> nodes = Lists.newArrayList(node);
        for (Node child : node.getOthersNodes().get(OtherLinkType.AST).asList()) {
            nodes.addAll(collectNode(child));
        }
        return nodes;
    }

    private void createAndCacheVertex(List<Node> nodeList) {
        nodeList.forEach(n -> {
            if (vertexCache.containsKey(n.hashCode(true))) {
                throw new RuntimeException("Not a tree.");
            }
            vertexCache.put(n.hashCode(true), createVertex(n));
        });
    }

    private TinkerVertex createVertex(Node node) {
        TinkerVertex vertex = (TinkerVertex) graph.addVertex("id", id++);
        setAttributes(node, vertex);
        return vertex;
    }

    private void setAttributes(Node node, TinkerVertex vertex) {
        vertex.property("type", node.getClass().getSimpleName());
        if(node.getParentNode().isPresent()) {
            vertex.property("parentType", node.getParentNode().get().getClass().getSimpleName());
        }
        vertex.property("text", node.toString());
        for (Map.Entry<String, String> entry : graphAttributeParser.getAttributes(node).entrySet()) {
            vertex.property(entry.getKey(), entry.getValue());
        }
    }

    private void setReference(TinkerVertex vertex, List<String> references) {
        vertex.property("reference", StringUtils.join(references, ","));
    }
}
