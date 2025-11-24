package com.liris.demo;

import weka.classifiers.Classifier;
import weka.classifiers.trees.RandomForest;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.converters.ConverterUtils.DataSource;
import weka.core.converters.CSVLoader;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class TrainModels {

    public static void main(String[] args) throws Exception {
        trainAndSave("weka/student-mat.arff", "weka/modeloRF_mat_bin.model");
        trainAndSave("weka/student-por.arff", "weka/modeloRF_por_bin.model");
        System.out.println("Entrenamiento completado y modelos guardados en /weka/");
    }

    private static void trainAndSave(String arffPath, String outputModelPath) throws Exception {
        System.out.println("Cargando: " + arffPath);
        DataSource ds = new DataSource(arffPath);
        Instances data = ds.getDataSet();

        // Buscar índice de G3
        Attribute g3Attr = data.attribute("G3");
        if (g3Attr == null) {
            // Intentar cargar CSV equivalente desde la carpeta data/ (separador ';')
            try {
                String csvPath = arffPath.replaceFirst("^weka/", "data/").replaceAll("\\.arff$", ".csv");
                System.out.println("Atributo G3 no encontrado en ARFF, intentando CSV: " + csvPath);
                CSVLoader loader = new CSVLoader();
                loader.setFieldSeparator(";");
                loader.setSource(new java.io.File(csvPath));
                data = loader.getDataSet();
                g3Attr = data.attribute("G3");
                if (g3Attr == null) {
                    throw new IllegalStateException("No se encontró el atributo G3 en ninguno de: " + arffPath + " o " + csvPath);
                }
            } catch (Exception ex) {
                throw new IllegalStateException("No se encontró el atributo G3 en " + arffPath + " y la carga CSV falló: " + ex.getMessage(), ex);
            }
        }
        int g3Idx = g3Attr.index();

        // Crear atributo binario 'pass' {no,yes}
        List<String> vals = Arrays.asList("no", "yes");
        Attribute passAttr = new Attribute("pass", vals);

        // Añadir al final
        data.insertAttributeAt(passAttr, data.numAttributes());
        int passIdx = data.attribute("pass").index();

        // Rellenar pass según G3 (>=10 -> yes)
        for (int i = 0; i < data.numInstances(); i++) {
            double g3 = data.instance(i).value(g3Idx);
            String label = (Double.isNaN(g3) ? "no" : (g3 >= 10.0 ? "yes" : "no"));
            data.instance(i).setValue(passIdx, label);
        }

        // Eliminar G3 para evitar fuga de información
        data.deleteAttributeAt(g3Idx);

        // Establecer clase en 'pass'
        data.setClassIndex(data.attribute("pass").index());

        System.out.println("Instancias para entrenamiento: " + data.numInstances() + ", atributos=" + data.numAttributes());

        // Entrenar RandomForest
        RandomForest rf = new RandomForest();
        rf.buildClassifier(data);

        // Crear carpeta si no existe
        File out = new File(outputModelPath).getParentFile();
        if (out != null && !out.exists()) out.mkdirs();

        SerializationHelper.write(outputModelPath, rf);
        System.out.println("Modelo guardado en: " + outputModelPath);
    }
}
