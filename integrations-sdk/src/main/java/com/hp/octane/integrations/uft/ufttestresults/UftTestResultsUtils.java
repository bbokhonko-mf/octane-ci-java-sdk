package com.hp.octane.integrations.uft.ufttestresults;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.hp.octane.integrations.uft.ufttestresults.schema.ReportNode;
import com.hp.octane.integrations.uft.ufttestresults.schema.ReportResults;
import com.hp.octane.integrations.uft.ufttestresults.schema.UftResultData;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class UftTestResultsUtils {

    private static Set<String> notFinalNodeType = new HashSet<>(Arrays.asList("Iteration", "Action", "Context"));

    public static List<UftResultData> getErrorData(File file) {
        ReportResults result = fromXml(file, ReportResults.class);
        List<UftResultData> errors = new ArrayList<>();
        List<String> parents = new ArrayList<>();
        getErrorDataInternal(result.getReportNode(), parents, errors);
        return errors;
    }

    public static String getAggregatedErrorMessage(List<UftResultData> errors) {
        String err = errors.stream()
                .map(e -> e.getMessage().trim() + ((e.getResult().equalsIgnoreCase("Warning")) ? " (Warning)" : ""))
                .map(msg -> msg + (msg.endsWith(".") ? "" : ". "))
                .distinct()
                .collect(Collectors.joining("\n"));
        return err;
    }

    public static List<UftResultData> getMBTData(File file) {
        ReportResults result = fromXml(file, ReportResults.class);
        List<UftResultData> data = new ArrayList<>();
        List<String> parents = new ArrayList<>();
        getMBTDataInternal(result.getReportNode(), parents, data, "Action", 4);
        return data;
    }

    private static void getErrorDataInternal(ReportNode node, List<String> parents, List<UftResultData> errors) {
        parents.add(node.getData().getName());
        boolean hasDescription = isNotEmpty(node.getData().getDescription());
        boolean failed = "Failed".equalsIgnoreCase(node.getData().getResult()) || "Warning".equalsIgnoreCase(node.getData().getResult());
        if (failed) {
            if (!notFinalNodeType.contains(node.getType()) && hasDescription) {
                //String parentStr = String.join("/", parents.subList(1, parents.size()));
                String error = /*parentStr + ":" +*/ (isNotEmpty(node.getData().getErrorText()) ? node.getData().getErrorText() : node.getData().getDescription());
                error = error.replace("Verify that this object's properties match an object currently displayed in your application.", "")
                        .replace("\n", "")
                        .replace("&nbsp;", " ")
                        .trim();

                //last parent name might be as error message - in this case - don't show last parent
                if (!parents.isEmpty() && error.startsWith(parents.get(parents.size() - 1))) {
                    parents.remove(parents.size() - 1);
                }
                errors.add(new UftResultData(parents, node.getType(), node.getData().getResult(), error, node.getData().getDuration()));
            }
            if (node.getNodes() != null) {
                node.getNodes().forEach(n -> getErrorDataInternal(n, new ArrayList<>(parents), errors));
            }
        }
    }

    private static void getMBTDataInternal(ReportNode node, List<String> parents, List<UftResultData> results, String nodeType, int targetLevel) {
        parents.add(node.getData().getName());

        boolean failed = "Failed".equalsIgnoreCase(node.getData().getResult()) || "Warning".equalsIgnoreCase(node.getData().getResult());


        if (parents.size() == targetLevel && nodeType.equals(node.getType())) {

            String errorMessage = "";
            if (failed) {

                List<UftResultData> errors = new ArrayList<>();
                getErrorDataInternal(node, new ArrayList(parents), errors);
                errorMessage = getAggregatedErrorMessage((errors));
            }

            results.add(new UftResultData(parents, node.getType(), node.getData().getResult(), errorMessage, node.getData().getDuration()));
        }

        if (node.getNodes() != null && parents.size() < targetLevel) {
            node.getNodes().forEach(n -> getMBTDataInternal(n, new ArrayList<>(parents), results, nodeType, targetLevel));
        }
    }

    private static boolean isNotEmpty(String str) {
        return !(str == null || str.isEmpty());
    }

    public static <T> T fromXml(File xml, Class<T> clazz) {
        try {
            XmlMapper xmlMapper = new XmlMapper();
            xmlMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
            T obj = xmlMapper.readValue(xml, clazz);

            return obj;
        } catch (Exception e) {
            throw new IllegalStateException("Error while deserializing a XML file to Object of type " + clazz, e);
        }
    }
}
