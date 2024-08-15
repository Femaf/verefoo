package it.polito.verefoo.test;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.polito.verefoo.VerefooSerializer;
import it.polito.verefoo.extra.TestCaseGeneratorFatTree;
import it.polito.verefoo.jaxb.NFV;
import it.polito.verefoo.utils.TestResults;

public class TestPerformanceFatTree {
    private static final Logger logger = LoggerFactory.getLogger(TestPerformanceFatTree.class);
    private String algo;
    private int numberPods = 4; // Number of pods in the Fat-Tree topology
    private int numberClientsPerPod = 2; // Number of clients per pod
    private int numberServersPerPod = 2; // Number of servers per pod
    private int runs = 100; // Number of iterations for the tests
    private int seed = 12345; // Seed for random generation
    private PrintWriter fileWriter; // Add this object for file writing

    @Before
    public void setUp() {
        // Initial configuration if needed, for example, asking which algorithm to test
        algo = "AP"; // Here you can set the algorithm according to your needs or parameterize the test
        try {
            // Initialize the writer to write to file
            fileWriter = new PrintWriter(new FileWriter("performance_test_output.log", true));
        } catch (Exception e) {
            e.printStackTrace();
            fail("Unable to create the log file");
        }
    }

    @Test
    public void testScalabilityPerformance() {
        Random rand = new Random(seed);
        String pathfile = "FatTree_P" + numberPods + "_C" + numberClientsPerPod + "_S" + numberServersPerPod + ".log";

        int[] seeds = new int[runs];
        for (int m = 0; m < runs; m++) {
            seeds[m] = Math.abs(rand.nextInt());
        }

        try {
            // Initialization of the Fat-Tree generator
            List<TestCaseGeneratorFatTree> nfvGenerators = new ArrayList<>();
            nfvGenerators.add(new TestCaseGeneratorFatTree("FatTree Test Generator", numberPods, numberClientsPerPod, numberServersPerPod, seed));

            for (TestCaseGeneratorFatTree generator : nfvGenerators) {
                // Creating the JAXB context to manage the XML file
                JAXBContext jc = JAXBContext.newInstance("it.polito.verefoo.jaxb");
                Unmarshaller u = jc.createUnmarshaller();
                SchemaFactory sf = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
                Schema schema = sf.newSchema(new File("./xsd/nfvSchema.xsd"));
                u.setSchema(schema);
                Marshaller m = jc.createMarshaller();
                m.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
                m.setProperty(Marshaller.JAXB_NO_NAMESPACE_SCHEMA_LOCATION, "./xsd/nfvSchema.xsd");

                for (int k = 0; k < runs; k++) {
                    try {
                        NFV nfv = generator.generateNFV(numberPods, numberClientsPerPod, numberServersPerPod, new Random(seeds[k]));
                        m.marshal(nfv, System.out); // Debug, prints the XML output
                        NFV resultNFV = testCoarse(nfv);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.toString());
        }
    }

    private NFV testCoarse(NFV root) throws Exception {
        long beginAll = System.currentTimeMillis();
        VerefooSerializer test = new VerefooSerializer(root, algo); // Selecting the algorithm
        long endAll = System.currentTimeMillis();
        TestResults results = test.getTestTimeResults();

        long totalTime = endAll - beginAll;
        long atomicPredCompTime = results.getAtomicPredCompTime();
        long atomicFlowsCompTime = results.getAtomicFlowsCompTime();
        long maxSMTtime = endAll - results.getBeginMaxSMTTime();

        String resString = "Total time " + totalTime + "ms, atomicPredCompTime " + atomicPredCompTime +
                           "ms, atomicFlowsCompTime " + atomicFlowsCompTime + "ms, maxSMT time " + maxSMTtime + "ms;";

        System.out.println(resString);
        logger.info(totalTime + "\t" + atomicPredCompTime + "\t" + atomicFlowsCompTime + "\t" + maxSMTtime + "\t" + results.getZ3Result() + "\t");

        // Write the output to file
        fileWriter.println(resString);
        fileWriter.flush(); // Ensure data is written immediately

        return test.getResult();
    }

    @After
    public void tearDown() {
        // Cleanup if necessary
        if (fileWriter != null) {
            fileWriter.close(); // Closes the file at the end of the test
        }
    }
}
