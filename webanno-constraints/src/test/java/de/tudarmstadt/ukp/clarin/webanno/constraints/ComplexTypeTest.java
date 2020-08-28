/*
 * Copyright 2015
 * Ubiquitous Knowledge Processing (UKP) Lab and FG Language Technology
 * Technische Universität Darmstadt
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.clarin.webanno.constraints;

import static de.tudarmstadt.ukp.clarin.webanno.constraints.parser.ConstraintsParser.parseFile;
import static de.tudarmstadt.ukp.clarin.webanno.support.uima.AnnotationBuilder.buildAnnotation;
import static de.tudarmstadt.ukp.clarin.webanno.support.uima.FeatureStructureBuilder.buildFS;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.CasCreationUtils;
import org.junit.Before;
import org.junit.Test;

import de.tudarmstadt.ukp.clarin.webanno.constraints.evaluator.Evaluator;
import de.tudarmstadt.ukp.clarin.webanno.constraints.evaluator.PossibleValue;
import de.tudarmstadt.ukp.clarin.webanno.constraints.evaluator.ValuesGenerator;
import de.tudarmstadt.ukp.clarin.webanno.constraints.model.ParsedConstraints;

public class ComplexTypeTest
{
    private CAS cas;
    private Evaluator sut;
    
    @Before
    public void setup() throws ResourceInitializationException
    {
        TypeSystemDescription tsd = TypeSystemDescriptionFactory
                .createTypeSystemDescription("desc.types.TestTypeSystemDescriptor");

        cas = CasCreationUtils.createCas(tsd, null, null);
        sut = new ValuesGenerator();
    }
    
    @Test
    public void thatSlotFeatureInConditionWorks()
            throws Exception
    {
        cas.setDocumentText("ACME acquired Foobar.");
        
        AnnotationFS host = buildAnnotation(cas, "webanno.custom.ComplexLinkHost")
            .on("acquired")
            .withFeature("links", asList(
                    buildFS(cas, "webanno.custom.ComplexLinkType")
                        .withFeature("target", buildAnnotation(cas, "webanno.custom.Span")
                                .on("ACME")
                                .withFeature("value", "PER")
                                .buildAndAddToIndexes())
                        .buildWithoutAddingToIndexes(),
                    buildFS(cas, "webanno.custom.ComplexLinkType")
                        .withFeature("target", buildAnnotation(cas, "webanno.custom.Span")
                                .on("Foobar")
                                .withFeature("value", "LOC")
                                .buildAndAddToIndexes())
                        .buildWithoutAddingToIndexes()))
            .buildAndAddToIndexes();
        
        ParsedConstraints constraints = parseFile(
                "src/test/resources/rules/slot_feature_in_condition.rules");

        List<PossibleValue> possibleValues = sut.generatePossibleValues(host, "value",
                constraints);

        assertThat(possibleValues)
                .containsExactly(new PossibleValue("move", false));
    }
    
    @Test
    public void thatFeaturePathInConditionWorks()
        throws Exception
    {
        cas.setDocumentText("I listen to lectures by Prof. Gurevych sometimes.");

        AnnotationFS proemel = buildAnnotation(cas, "de.tud.Prof")
                .withFeature("fullName", "Hans Juergen Proeml")
                .buildAndAddToIndexes();

        AnnotationFS gurevych = buildAnnotation(cas, "de.tud.Prof")
                .withFeature("fullName", "Iryna Gurevych")
                .withFeature("boss", proemel)
                .buildAndAddToIndexes();

        ParsedConstraints constraints = parseFile("src/test/resources/rules/feature_path_in_condition.rules");

        List<PossibleValue> possibleValues = sut.generatePossibleValues(gurevych, "professorName",
                constraints);

        assertThat(possibleValues)
                .containsExactly(new PossibleValue("Iryna Gurevych", false));
    }

    @Test
    public void thatConjunctionInConditionWorks()
        throws Exception
    {
        cas.setDocumentText("Asia is the largest continent on Earth. Asia is subdivided into 48 "
                + "countries, two of them (Russia and Turkey) having part of their land in "
                + "Europe. The most active place on Earth for tropical cyclone activity lies "
                + "northeast of the Philippines and south of Japan. The Gobi Desert is in "
                + "Mongolia and the Arabian Desert stretches across much of the Middle East. "
                + "The Yangtze River in China is the longest river in the continent. The "
                + "Himalayas between Nepal and China is the tallest mountain range in the "
                + "world. Tropical rainforests stretch across much of southern Asia and "
                + "coniferous and deciduous forests lie farther north.");

        AnnotationFS asiaContinent = buildAnnotation(cas, "de.Continent")
                .at(0, 4)
                .withFeature("name", "Asia")
                .buildAndAddToIndexes();

        AnnotationFS russia = buildAnnotation(cas, "de.Country")
                .at(56, 62)
                .withFeature("name", "Russian Federation")
                .withFeature("continent", asiaContinent)
                .buildAndAddToIndexes();
                
        ParsedConstraints constraints = parseFile("src/test/resources/rules/region.rules");

        List<PossibleValue> possibleValues = sut.generatePossibleValues(russia, "regionType",
                constraints);

        assertThat(possibleValues)
                .containsExactly(new PossibleValue("cold", true));
    }
    
    @Test
    public void thatBooleanValueInConditionWorks() throws Exception
    {
        cas.setDocumentText("blah");
        
        AnnotationFS continent = buildAnnotation(cas, "de.Continent")
                .at(0, 1)
                .withFeature("discovered", true)
                .buildAndAddToIndexes();
        
        ParsedConstraints constraints = parseFile("src/test/resources/rules/region.rules");

        List<PossibleValue> possibleValues = sut.generatePossibleValues(continent, "name",
                constraints);
        
        assertThat(possibleValues)
                .extracting(PossibleValue::getValue)
                .containsExactlyInAnyOrder("America");
    }
    
    @Test
    public void thatIntegerValueInConditionWorks() throws Exception
    {
        cas.setDocumentText("blah");
        
        AnnotationFS continent = buildAnnotation(cas, "de.Continent")
                .at(0, 1)
                .withFeature("area", 100)
                .buildAndAddToIndexes();

        ParsedConstraints constraints = parseFile("src/test/resources/rules/region.rules");

        List<PossibleValue> possibleValues = sut.generatePossibleValues(continent, "name",
                constraints);
        
        assertThat(possibleValues)
                .extracting(PossibleValue::getValue)
                .containsExactlyInAnyOrder("Fantasy Island");
    }
}
