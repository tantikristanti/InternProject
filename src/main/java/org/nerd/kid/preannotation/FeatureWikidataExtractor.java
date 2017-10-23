package org.nerd.kid.preannotation;

import org.nerd.kid.arff.ArffParser;
import org.nerd.kid.preprocessing.CSVFileReader;
import org.nerd.kid.rest.DataPredictor;
import org.wikidata.wdtk.datamodel.interfaces.*;
import org.wikidata.wdtk.wikibaseapi.WikibaseDataFetcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/*
* extract the features (properties and values) of WikidataId directly from Wikidata knowledge base
* */

public class FeatureWikidataExtractor {
    // object of Wikidata's data fetcher
    private WikibaseDataFetcher wikibaseDataFetcher = WikibaseDataFetcher.getWikidataDataFetcher();
    DataPredictor predictData = new DataPredictor();

    public void getFeatureWikidata(File fileTraining, File fileTesting) throws Exception{
        //read properties needed from training file
        ArffParser arffParser = new ArffParser();
        List<String> listProperties = arffParser.readPropertiesTrainingFile(new File("data/Training.arff")); // fileTraining

        // wikidataId to be extracted
        CSVFileReader readCSVFile = new CSVFileReader();
        ArrayList<String> dataWikiId = readCSVFile.readCsv("data/preannotation/dataPreannotation.csv"); // fileTesting

        // number of row and column needed
        int rowNumber = dataWikiId.size();
        int colNumberTestX = listProperties.size();
        int colNumberNewData = 3;

        double[][] testX = new double[rowNumber][colNumberTestX];
        String[][] matrixNewData = new String[rowNumber][colNumberNewData];

        List<String> labelWiki = new ArrayList<String>();

        // rows are for examples, columns are for properties and its class
        System.out.println("Total row to be processed : " + rowNumber);
        for (int i = 0; i < rowNumber; i++) {
            System.out.println("Processing data row " + i);
            // getting every element of Wikidata Id
            String elementWikiId = dataWikiId.get(i);

            // getting entity document
            EntityDocument QElementWikiId = wikibaseDataFetcher.getEntityDocument(elementWikiId);
            // getting the label of wikidata element
            if (QElementWikiId instanceof ItemDocument) {
                String labelItem = ((ItemDocument) QElementWikiId).getLabels().get("en").getText();
                labelWiki.add(labelItem);
            }

            // fetching data
            ItemDocument itemDocument = (ItemDocument) wikibaseDataFetcher.getEntityDocument(elementWikiId);

            // checking if item document is null
            if (itemDocument == null) {
                System.out.println("Data couldn't be fetched.");
                break;
            }

            // list for storing properties data and its values
            List<String> dataPropertyWiki = new ArrayList<String>();
            List<String> dataValuePropertyWiki = new ArrayList<String>();

            // only get the value of P31 (instance of) and P21 (sex or gender)
            List keySearch = new ArrayList();
            keySearch.add("P31");
            keySearch.add("P21");

            // getting the properties from Wikidata
            for (StatementGroup statementGroup : itemDocument.getStatementGroups()) {
                String property = statementGroup.getProperty().getId().toString().trim();
                dataPropertyWiki.add(property);

                // getting the value of property P31 and P21
                for (int k = 0; k < keySearch.size(); k++) {
                    if (!keySearch.get(k).equals(property))
                        continue;
                    for (Statement statement : statementGroup) {
                        if (statement.getClaim().getMainSnak() instanceof ValueSnak) {
                            Value value = ((ValueSnak) statement.getClaim().getMainSnak()).getValue();
                            if (value instanceof ItemIdValue) {
                                String valueOfProperty = ((ItemIdValue) value).getId().toString().trim();
                                String combinationValueProperty = property + "_" + valueOfProperty;
                                dataValuePropertyWiki.add(combinationValueProperty);
                            }
                        }
                    }
                }
            }

            // fill the testing file with the input matrix x (for testing arff file)
            for (int j = 0; j < colNumberTestX; j++) {
                // search based on the list of properties in training file
                String propertySearched = listProperties.get(j);

                // if properties of a certain Wikidata Id match with the list of properties in training file, give it a binary value 1
                for (int k = 0; k < dataPropertyWiki.size(); k++) {
                    if (propertySearched.equals(dataPropertyWiki.get(k))) {
                        testX[i][j] = 1.0;
                        break;
                    }
                }

                // if the combination of properties-values of a certain Wikidata Id match with the list of properties-values combination in training file, give it a binary value 1
                for (int k = 0; k < dataValuePropertyWiki.size(); k++) {
                    if (propertySearched.equals(dataValuePropertyWiki.get(k))) {
                        testX[i][j] = 1.0;
                        break;
                    }
                }
            }

            // add another data column needed for testing file
            for (int j = 0; j < 3; j++) {
                if (j == 0) { // first column : Wikidata Id
                    matrixNewData[i][j] = elementWikiId;
                } else if (j == 1) { //second column : label of Wikidata Id
                    matrixNewData[i][j] = labelWiki.get(i);
                } else if (j == 2) { // third column : predicted data
                    matrixNewData[i][j] = "Null";
                }
            } // end of column of matrix
        } // end of row of matrix

        // get the result of prediction and put it in matrix new data
        String[] resultPredict = predictData.predictNewTestData(testX);
        for (int i = 0; i < rowNumber; i++) {
            matrixNewData[i][2] = resultPredict[i];
        }

        // print the header for the result
        System.out.print("WikidataID" + ";" + "labelWikidata" + ";" + "PredictedClass\n");
        // print the result of matrixNewData
        for (int i = 0; i < rowNumber; i++) {
            for (int j = 0; j < colNumberNewData; j++) {
                System.out.print(matrixNewData[i][j] + "\t");
            }
            System.out.print("\n");
        }

        // print the result into file
        printResultWikidataExtraction(matrixNewData);

    } // end of method getFeatureWikidata

    public void printResultWikidataExtraction(String[][] matrix) throws Exception{
        PrintStream printStream = new PrintStream(new FileOutputStream("result/Predicted_Result.csv"));
        printStream.print("WikidataID" + ";" + "labelWikidata" + ";" + "PredictedClass\n");
        // print the result
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                printStream.print(matrix[i][j]);
                if (j != matrix[i].length - 1) {
                    printStream.print(";");
                }
            }
            printStream.print("\n");
        }
        printStream.flush();
        printStream.close();
    }

} // end of class FeatureWikidataExtractor
