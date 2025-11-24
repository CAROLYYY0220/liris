package com.liris.demo;

import org.springframework.stereotype.Service;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NumericToNominal;
import weka.filters.unsupervised.attribute.Remove;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class WekaService {

    private Instances dataset;
    private Classifier model;

    public void loadDataset() throws Exception {
        InputStream csvStream = getClass().getResourceAsStream("/data/student-mat.csv");
        if (csvStream == null) {
            throw new RuntimeException("Dataset not found: /data/student-mat.csv");
        }

        CSVLoader loader = new CSVLoader();
        loader.setFieldSeparator(";");
        loader.setSource(csvStream);
        Instances rawData = loader.getDataSet();

        // The target variable is G3 (index 32, 0-based).
        // We want to predict pass/fail.
        // Let's assume pass is G3 >= 10.
        // Since G3 is numeric, we can either do regression or classification.
        // For "identify if a person approves or disapproves", classification is better.
        // We need to convert the numeric G3 to a nominal class.

        // However, Weka's filters are powerful. Let's do a custom transformation or use a filter.
        // A simple way is to iterate and change the value, but modifying Instances structure is tricky.
        // Let's use a trick: Add a new nominal attribute "class" based on G3, then remove G3.
        
        // 1. Add class attribute
        ArrayList<String> labels = new ArrayList<>();
        labels.add("desaprobado");
        labels.add("aprobado");
        Attribute classAttribute = new Attribute("class", labels);
        
        Instances newData = new Instances(rawData);
        newData.insertAttributeAt(classAttribute, newData.numAttributes());
        
        // 2. Set values for the new attribute based on G3
        int g3Index = rawData.attribute("G3").index();
        int classIndex = newData.attribute("class").index();
        
        for (int i = 0; i < rawData.numInstances(); i++) {
            double g3Value = rawData.instance(i).value(g3Index);
            String label = (g3Value >= 10.0) ? "aprobado" : "desaprobado";
            newData.instance(i).setValue(classIndex, label);
        }
        
        // 3. Remove G3 (and maybe G1, G2 if we want to predict from demographics only, but usually G1/G2 are strong predictors)
        // The user asked to "identify", usually implying prediction.
        // If we keep G1/G2, accuracy will be very high. If we remove them, it's harder.
        // I'll keep them for now as standard "student performance" prediction often uses them.
        // But I must remove the original G3 numeric attribute to avoid leakage if I used it to derive class.
        // Actually, if I keep G3 numeric and set class, the model might just learn "if G3 >= 10".
        // So I MUST remove G3 numeric attribute.
        
        // Remove G3 (which is at index g3Index in original, same index in new data before insertion? No, insert is at end)
        // Original G3 is at 32. New attribute at 33.
        // Let's remove the original G3.
        
        Remove remove = new Remove();
        remove.setAttributeIndices((g3Index + 1) + ""); // 1-based index
        remove.setInputFormat(newData);
        dataset = Filter.useFilter(newData, remove);
        
        // Set class index to the last attribute (our new "class")
        dataset.setClassIndex(dataset.numAttributes() - 1);
    }

    public String trainModel() throws Exception {
        if (dataset == null) {
            loadDataset();
        }

        // Use Random Forest
        model = new RandomForest();
        model.buildClassifier(dataset);

        return model.toString();
    }

    public String evaluateModel() throws Exception {
        if (model == null) {
            trainModel();
        }

        Evaluation eval = new Evaluation(dataset);
        eval.crossValidateModel(model, dataset, 10, new Random(1));

        return eval.toSummaryString("\nResults\n======\n", false) + 
               "\nConfusion Matrix\n================\n" + 
               eval.toMatrixString();
    }
    
    public Classifier getModel() {
        return model;
    }
    
    public Instances getDataset() {
        return dataset;
    }
}
