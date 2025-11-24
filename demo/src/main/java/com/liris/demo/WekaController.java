package com.liris.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.converters.ConverterUtils.DataSource;
import weka.core.converters.CSVLoader;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.stream.Collectors;
import weka.core.DenseInstance;
import weka.core.Attribute;
import java.util.Enumeration;

@Controller
public class WekaController {

    private static final Logger logger = LoggerFactory.getLogger(WekaController.class);

    private Classifier modeloMat;
    private Classifier modeloPor;
    private Instances datasetMat;
    private Instances datasetPor;

    public WekaController() {
        try {
            // helper local para abrir recursos probando classloader, Class.getResource y rutas en disco
            
            // Prefer loading original ARFFs from /weka because models were trained on ARFF
            InputStream isMat = openResource("weka/student-mat.arff");
            InputStream isPor = openResource("weka/student-por.arff");

            if (isMat != null && isPor != null) {
                datasetMat = new DataSource(isMat).getDataSet();
                datasetPor = new DataSource(isPor).getDataSet();
                // Si el ARFF está malformado (atributos inesperadamente pocos o falta G3), usar CSV como fallback
                if (datasetMat.numAttributes() < 3 || datasetMat.attribute("G3") == null) {
                    System.out.println("ARFF de MAT parece incorrecto; cargando CSV de fallback.");
                    InputStream csvMat = openResource("data/student-mat.csv");
                    if (csvMat != null) {
                        CSVLoader loaderMat = new CSVLoader();
                        loaderMat.setFieldSeparator(";");
                        loaderMat.setSource(csvMat);
                        datasetMat = loaderMat.getDataSet();
                    }
                }
                if (datasetPor.numAttributes() < 3 || datasetPor.attribute("G3") == null) {
                    System.out.println("ARFF de POR parece incorrecto; cargando CSV de fallback.");
                    InputStream csvPor = openResource("data/student-por.csv");
                    if (csvPor != null) {
                        CSVLoader loaderPor = new CSVLoader();
                        loaderPor.setFieldSeparator(";");
                        loaderPor.setSource(csvPor);
                        datasetPor = loaderPor.getDataSet();
                    }
                }
            } else {
                // Fallback a CSV en /data
                InputStream csvMat = openResource("data/student-mat.csv");
                InputStream csvPor = openResource("data/student-por.csv");

                if (csvMat == null || csvPor == null) {
                    throw new IllegalStateException("No se encontraron los archivos de datos en classpath '/weka/' ni '/data/'.");
                }

                CSVLoader loaderMat = new CSVLoader();
                loaderMat.setFieldSeparator(";");
                loaderMat.setSource(csvMat);
                datasetMat = loaderMat.getDataSet();

                CSVLoader loaderPor = new CSVLoader();
                loaderPor.setFieldSeparator(";");
                loaderPor.setSource(csvPor);
                datasetPor = loaderPor.getDataSet();
            }

            // Intentar establecer la clase por nombre 'G3' si existe, sino usar el último atributo
            if (datasetMat.attribute("G3") != null) {
                datasetMat.setClassIndex(datasetMat.attribute("G3").index());
            } else {
                datasetMat.setClassIndex(datasetMat.numAttributes() - 1);
            }

            if (datasetPor.attribute("G3") != null) {
                datasetPor.setClassIndex(datasetPor.attribute("G3").index());
            } else {
                datasetPor.setClassIndex(datasetPor.numAttributes() - 1);
            }

            // Debug: imprimir estructura de los datasets cargados
            System.out.println("Dataset MAT attributes: " + datasetMat.numAttributes() + ", instances=" + datasetMat.numInstances() + ", classIndex=" + datasetMat.classIndex() + ", classIsNumeric=" + datasetMat.classAttribute().isNumeric());
            System.out.println("Dataset POR attributes: " + datasetPor.numAttributes() + ", instances=" + datasetPor.numInstances() + ", classIndex=" + datasetPor.classIndex() + ", classIsNumeric=" + datasetPor.classAttribute().isNumeric());

            // Cargar modelos desde classpath (preferir modelos binarios '*_bin.model')
            InputStream mMat = openResource("weka/modeloRF_mat_bin.model");
            if (mMat == null) mMat = openResource("weka/modeloRF.model");

            InputStream mPor = openResource("weka/modeloRF_por_bin.model");
            if (mPor == null) mPor = openResource("weka/modeloRF_por.model");

            // Cargar cada modelo individualmente y emitir advertencias por separado
            if (mMat == null) {
                System.err.println("Advertencia: no se encontró modelo MAT en '/weka/'.");
            } else {
                modeloMat = (Classifier) SerializationHelper.read(mMat);
            }

            if (mPor == null) {
                System.err.println("Advertencia: no se encontró modelo POR en '/weka/'.");
            } else {
                modeloPor = (Classifier) SerializationHelper.read(mPor);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @GetMapping("/predicciones")
    public String mostrarPredicciones(Model model) {
        List<Map<String, Object>> predsMat = predecirConReal(datasetMat, modeloMat);
        List<Map<String, Object>> predsPor = predecirConReal(datasetPor, modeloPor);

        long matAprobados = predsMat.stream().filter(m -> "aprobado".equals(m.get("resultado"))).count();
        long porAprobados = predsPor.stream().filter(m -> "aprobado".equals(m.get("resultado"))).count();

        model.addAttribute("prediccionesMat", predsMat);
        model.addAttribute("prediccionesPor", predsPor);
        model.addAttribute("matAprobados", matAprobados);
        model.addAttribute("matTotal", predsMat.size());
        model.addAttribute("porAprobados", porAprobados);
        model.addAttribute("porTotal", predsPor.size());
        return "prediccionesView"; // nombre del template HTML (evita conflicto con la URL /predicciones)
    }

    @GetMapping("/api/predicciones")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiPredicciones() {
        logger.info("/api/predicciones called");
        List<Map<String, Object>> predsMat = predecirConReal(datasetMat, modeloMat);
        List<Map<String, Object>> predsPor = predecirConReal(datasetPor, modeloPor);
        long matAprobados = predsMat.stream().filter(m -> "aprobado".equals(m.get("resultado"))).count();
        long porAprobados = predsPor.stream().filter(m -> "aprobado".equals(m.get("resultado"))).count();
        Map<String, Object> resp = new HashMap<>();
        resp.put("prediccionesMat", predsMat);
        resp.put("prediccionesPor", predsPor);
        resp.put("matAprobados", matAprobados);
        resp.put("matTotal", predsMat.size());
        resp.put("porAprobados", porAprobados);
        resp.put("porTotal", predsPor.size());
        return ResponseEntity.ok(resp);
    }

    @org.springframework.beans.factory.annotation.Autowired
    private WekaService wekaService;

    @GetMapping("/ping")
    @ResponseBody
    public ResponseEntity<String> ping() {
        logger.info("/ping called");
        return ResponseEntity.ok("pong");
    }

    @GetMapping("/debug/resources")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> debugResources() {
        Map<String, Object> info = new HashMap<>();
        String[] paths = new String[]{
                "weka/student-mat.arff",
                "weka/student-por.arff",
                "weka/modeloRF_mat_bin.model",
                "weka/modeloRF_por_bin.model",
                "weka/modeloRF.model",
                "weka/modeloRF_por.model",
                "data/student-mat.csv",
                "data/student-por.csv"
        };
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        for (String p : paths) {
            boolean foundByContext = cl.getResourceAsStream(p) != null;
            boolean foundByClass = getClass().getResourceAsStream("/" + p) != null;
            boolean foundInTarget = new java.io.File("target/classes/" + p).exists();
            boolean foundRelative = new java.io.File(p).exists();
            Map<String, Object> r = new HashMap<>();
            r.put("contextClassLoader", foundByContext);
            r.put("classGetResource", foundByClass);
            r.put("targetClassesPath", foundInTarget);
            r.put("relativePath", foundRelative);
            info.put(p, r);
        }
        return ResponseEntity.ok(info);
    }

    @GetMapping("/survey")
    public String surveyForm(Model model) {
        Instances header = buildPredictionHeader(datasetMat);
        model.addAttribute("datasetName", "mat");
        model.addAttribute("attributes", buildAttributeDescriptors(header));
        return "predict_form";
    }

    @GetMapping("/survey-short")
    public String surveyShort(Model model) {
        Instances header = buildPredictionHeader(datasetMat);
        List<Map<String, Object>> all = buildAttributeDescriptors(header);
        List<String> keep = Arrays.asList("G1", "G2", "studytime", "failures", "absences", "internet", "higher", "goout");
        List<Map<String, Object>> filtered = all.stream()
                .filter(m -> Boolean.FALSE.equals(m.get("class")))
                .filter(m -> keep.contains(m.get("name")))
                .collect(Collectors.toList());
        model.addAttribute("datasetName", "mat");
        model.addAttribute("attributes", filtered);
        return "predict_form";
    }

    @org.springframework.web.bind.annotation.PostMapping("/predict")
    public String predictFromForm(@org.springframework.web.bind.annotation.RequestParam Map<String, String> params, Model model) {
        try {
            String dataset = params.getOrDefault("dataset", "mat");
            Instances base = "por".equalsIgnoreCase(dataset) ? datasetPor : datasetMat;
            Instances header = buildPredictionHeader(base);

            weka.core.DenseInstance inst = new weka.core.DenseInstance(header.numAttributes());
            inst.setDataset(header);

            for (int i = 0; i < header.numAttributes(); i++) {
                weka.core.Attribute att = header.attribute(i);
                if (i == header.classIndex()) continue;
                String name = att.name();
                String val = params.get(name);
                if (val == null) val = params.get(name.toLowerCase());
                if (val == null) continue;
                if (att.isNumeric()) {
                    try { inst.setValue(i, Double.parseDouble(val)); } catch (Exception ex) { inst.setMissing(i); }
                } else if (att.isNominal()) {
                    int idx = att.indexOfValue(val);
                    if (idx == -1) {
                        String clean = val.replaceAll("\"", "").trim();
                        idx = att.indexOfValue(clean);
                    }
                    if (idx >= 0) inst.setValue(i, idx); else inst.setMissing(i);
                } else {
                    try { inst.setValue(i, val); } catch (Exception ex) { inst.setMissing(i); }
                }
            }

            Classifier cls = "por".equalsIgnoreCase(dataset) ? modeloPor : modeloMat;
            if (cls == null) throw new IllegalStateException("Modelo no cargado para: " + dataset);

            double predIdx = cls.classifyInstance(inst);
            String predLabel = header.classAttribute().value((int) predIdx);
            String resultado = (predLabel.equalsIgnoreCase("yes") || predLabel.toLowerCase().contains("aprob") ) ? "aprobado" : "desaprobado";

            model.addAttribute("predLabel", predLabel);
            model.addAttribute("resultado", resultado);
            model.addAttribute("dataset", dataset);
            return "predict_result";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", e.getMessage());
            return "error";
        }
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/predict")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiPredict(@org.springframework.web.bind.annotation.RequestParam Map<String, String> params) {
        Map<String, Object> resp = new HashMap<>();
        logger.info("/api/predict called with params={}", params);
        try {
            String dataset = params.getOrDefault("dataset", "mat");
            Instances base = "por".equalsIgnoreCase(dataset) ? datasetPor : datasetMat;
            Instances header = buildPredictionHeader(base);

            weka.core.DenseInstance inst = new weka.core.DenseInstance(header.numAttributes());
            inst.setDataset(header);

            for (int i = 0; i < header.numAttributes(); i++) {
                weka.core.Attribute att = header.attribute(i);
                if (i == header.classIndex()) continue;
                String name = att.name();
                String val = params.get(name);
                if (val == null) val = params.get(name.toLowerCase());
                if (val == null) continue;
                if (att.isNumeric()) {
                    try { inst.setValue(i, Double.parseDouble(val)); } catch (Exception ex) { inst.setMissing(i); }
                } else if (att.isNominal()) {
                    int idx = att.indexOfValue(val);
                    if (idx == -1) {
                        String clean = val.replaceAll("\"", "").trim();
                        idx = att.indexOfValue(clean);
                    }
                    if (idx >= 0) inst.setValue(i, idx); else inst.setMissing(i);
                } else {
                    try { inst.setValue(i, val); } catch (Exception ex) { inst.setMissing(i); }
                }
            }

            Classifier cls = "por".equalsIgnoreCase(dataset) ? modeloPor : modeloMat;
            if (cls == null) throw new IllegalStateException("Modelo no cargado para: " + dataset);

            double predIdx = Double.NaN;
            String predLabel = null;
            double confidence = Double.NaN;

            try {
                predIdx = cls.classifyInstance(inst);
            } catch (Exception ex) {
                double[] dist = cls.distributionForInstance(inst);
                if (dist != null && dist.length > 0) {
                    int max = 0; for (int j=1;j<dist.length;j++) if (dist[j]>dist[max]) max=j;
                    predIdx = max;
                    confidence = dist[max];
                }
            }

            try {
                if (!Double.isNaN(predIdx)) predLabel = header.classAttribute().value((int) predIdx);
            } catch (Exception ex) { predLabel = String.valueOf(predIdx); }

            if (Double.isNaN(confidence)) {
                try { double[] dist = cls.distributionForInstance(inst); if (dist!=null && dist.length>0) { int max=0; for (int j=1;j<dist.length;j++) if (dist[j]>dist[max]) max=j; confidence = dist[max]; } } catch(Exception e){ }
            }

            String resultado = (predLabel!=null && (predLabel.equalsIgnoreCase("yes") || predLabel.toLowerCase().contains("aprob"))) ? "aprobado" : "desaprobado";

            resp.put("predLabel", predLabel);
            resp.put("resultado", resultado);
            resp.put("dataset", dataset);
            resp.put("confidence", confidence);
            logger.info("/api/predict result: dataset={} predLabel={} resultado={} confidence={}", dataset, predLabel, resultado, confidence);

            // Append request+response to log file for auditing
            try {
                Path logsDir = Paths.get("logs");
                if (!Files.exists(logsDir)) Files.createDirectories(logsDir);
                Path logFile = logsDir.resolve("requests.log");
                String line = Instant.now().toString() + " | params=" + params.toString() + " | result=" + resp.toString() + System.lineSeparator();
                Files.write(logFile, line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (Exception ioe) {
                logger.warn("Could not write request log: {}", ioe.toString());
            }

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("/api/predict error", e);
            resp.put("error", e.getMessage());
            return ResponseEntity.status(500).body(resp);
        }
    }

    private List<Map<String, Object>> buildAttributeDescriptors(Instances header) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (header == null) return out;
        for (int i = 0; i < header.numAttributes(); i++) {
            weka.core.Attribute a = header.attribute(i);
            Map<String, Object> desc = new HashMap<>();
            desc.put("name", a.name());
            if (i == header.classIndex()) { desc.put("class", true); out.add(desc); continue; }
            desc.put("class", false);
            if (a.isNumeric()) {
                desc.put("type", "numeric");
            } else if (a.isNominal()) {
                desc.put("type", "nominal");
                List<String> vals = new ArrayList<>();
                Enumeration<Object> en = a.enumerateValues();
                while (en.hasMoreElements()) vals.add(String.valueOf(en.nextElement()));
                desc.put("values", vals);
            } else {
                desc.put("type", "string");
            }
            out.add(desc);
        }
        return out;
    }

    // Construye un header de Instances compatible con el modelo entrenado: copia el dataset base, añade atributo 'pass' y elimina 'G3'
    private Instances buildPredictionHeader(Instances base) {
        try {
            if (base == null) return null;
            Instances copy = new Instances(base);
            weka.core.Attribute g3 = copy.attribute("G3");
            if (g3 == null) g3 = copy.attribute("g3");
            // ensure class index is unset before removing attributes
            try { copy.setClassIndex(-1); } catch (Exception ex) { }
            if (copy.attribute("pass") == null) {
                List<String> vals = java.util.Arrays.asList("no", "yes");
                weka.core.Attribute passAttr = new weka.core.Attribute("pass", vals);
                copy.insertAttributeAt(passAttr, copy.numAttributes());
            }
            if (g3 != null) {
                try { copy.deleteAttributeAt(g3.index()); } catch (Exception ex) { System.err.println("Could not delete G3 attribute: " + ex.getMessage()); }
            }
            weka.core.Attribute pass = copy.attribute("pass");
            if (pass != null) copy.setClassIndex(pass.index());
            Instances header = new Instances(copy, 0);
            return header;
        } catch (Exception e) { e.printStackTrace(); return null; }
    }

    @GetMapping("/algorithm")
    public String runAlgorithm(Model model) {
        try {
            String evaluationResults = wekaService.evaluateModel();
            model.addAttribute("evaluationResults", evaluationResults);
            return "algorithmView";
        } catch (Exception e) {
            e.printStackTrace();
            model.addAttribute("error", e.getMessage());
            return "error";
        }
    }

    private List<Map<String, Object>> predecirConReal(Instances dataset, Classifier modelo) {
        List<Map<String, Object>> resultados = new ArrayList<>();
        try {
            if (dataset == null) {
                System.err.println("predecirConReal: dataset is null, returning empty results");
                return resultados;
            }

            // Construir header compatible con el modelo (sin G3 y con 'pass' como clase)
            Instances header = buildPredictionHeader(dataset);
            if (header == null) {
                System.err.println("predecirConReal: header is null, aborting predictions");
                return resultados;
            }

            // Si no hay modelo cargado, devolver sin intentar clasificar
            if (modelo == null) {
                for (int i = 0; i < dataset.numInstances(); i++) {
                    Map<String, Object> row = new HashMap<>();
                    row.put("index", i + 1);
                    Double actual = null;
                    try { if (dataset.classIndex() >= 0 && dataset.classAttribute().isNumeric()) actual = dataset.instance(i).value(dataset.classIndex()); } catch (Exception ex) { actual = null; }
                    row.put("actual", actual);
                    row.put("predictedGrade", null);
                    row.put("predictedLabel", "no_model");
                    row.put("resultado", "desconocido");
                    resultados.add(row);
                }
                return resultados;
            }

            for (int i = 0; i < dataset.numInstances(); i++) {
                Map<String, Object> row = new HashMap<>();
                weka.core.Instance orig = dataset.instance(i);

                // Construir instancia compatible con el header copiada desde la instancia original
                weka.core.DenseInstance inst = new weka.core.DenseInstance(header.numAttributes());
                inst.setDataset(header);
                for (int a = 0; a < header.numAttributes(); a++) {
                    if (a == header.classIndex()) continue;
                    Attribute att = header.attribute(a);
                    Attribute origAtt = dataset.attribute(att.name());
                    if (origAtt == null) { inst.setMissing(a); continue; }
                    try {
                        if (att.isNumeric()) {
                            double v = orig.value(origAtt.index());
                            inst.setValue(a, v);
                        } else if (att.isNominal()) {
                            String sval = null;
                            if (origAtt.isNumeric()) sval = String.valueOf(orig.value(origAtt.index()));
                            else sval = orig.stringValue(origAtt.index());
                            int vidx = att.indexOfValue(sval);
                            if (vidx >= 0) inst.setValue(a, vidx); else inst.setMissing(a);
                        } else {
                            try { inst.setValue(a, orig.stringValue(origAtt.index())); } catch (Exception ex) { inst.setMissing(a); }
                        }
                    } catch (Exception ex) { inst.setMissing(a); }
                }

                // obtener valor real si es posible
                Double actual = null;
                try { if (dataset.classIndex() >= 0 && dataset.classAttribute().isNumeric()) actual = orig.value(dataset.classIndex()); } catch (Exception ex) { actual = null; }
                row.put("index", i + 1);
                row.put("actual", actual);

                double rawPred = Double.NaN;
                boolean gotPrediction = false;
                try {
                    rawPred = modelo.classifyInstance(inst);
                    gotPrediction = true;
                } catch (Exception exClassify) {
                    System.err.println("classifyInstance failed for instance " + i + ": " + exClassify.getMessage());
                    try {
                        double[] dist = modelo.distributionForInstance(inst);
                        if (dist != null && dist.length > 0) {
                            int maxIdx = 0;
                            for (int j = 1; j < dist.length; j++) if (dist[j] > dist[maxIdx]) maxIdx = j;
                            rawPred = maxIdx;
                            gotPrediction = true;
                        }
                    } catch (Exception exDist) {
                        System.err.println("distributionForInstance also failed for instance " + i + ": " + exDist.getMessage());
                    }
                }

                if (!gotPrediction) {
                    row.put("predictedGrade", null);
                    row.put("predictedLabel", "error");
                    row.put("resultado", "desconocido");
                    resultados.add(row);
                    continue;
                }

                // modelo entrenado como binario: la clase en el header es nominal (pass)
                int predIndex = (int) rawPred;
                String predClase = "";
                if (predIndex >= 0 && predIndex < header.classAttribute().numValues()) predClase = header.classAttribute().value(predIndex);
                else predClase = String.valueOf(rawPred);
                row.put("predictedGrade", null);
                row.put("predictedLabel", predClase);
                String normalized = (predClase.toLowerCase().contains("aprob") || predClase.toLowerCase().contains("pass") || predClase.toLowerCase().contains("yes")) ? "aprobado" : "desaprobado";
                row.put("resultado", normalized);
                resultados.add(row);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultados;
    }

    // Abre un InputStream para el recurso dado probando: ContextClassLoader, Class.getResource, y rutas en disco
    private InputStream openResource(String path) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            InputStream is = cl.getResourceAsStream(path);
            if (is != null) return is;
            is = getClass().getResourceAsStream("/" + path);
            if (is != null) return is;
            java.io.File f = new java.io.File("target/classes/" + path);
            if (f.exists()) return new java.io.FileInputStream(f);
            f = new java.io.File(path);
            if (f.exists()) return new java.io.FileInputStream(f);
        } catch (Exception e) {
            System.err.println("openResource failed for " + path + ": " + e.getMessage());
        }
        return null;
    }
}

