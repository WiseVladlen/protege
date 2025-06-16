package org.protege.editor.owl.neo4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.neo4j.driver.Session;
import org.semanticweb.owlapi.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class OntologyChangeListenerToNeo4j implements OWLOntologyChangeListener {
    private static final Logger logger = LoggerFactory.getLogger(OntologyChangeListenerToNeo4j.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Session session;
    private final SimpleHttpClient httpClient;
    private final Supplier<Boolean> availabilityChecker;

    public OntologyChangeListenerToNeo4j(@Nonnull Session session, ElementEventHandler elementEventHandler, Supplier<Boolean> availabilityChecker) {
        this.session = session;
        this.httpClient = new SimpleHttpClient(elementEventHandler);
        this.availabilityChecker = availabilityChecker;
    }

    @Override
    public void ontologiesChanged(@Nonnull List<? extends OWLOntologyChange> changes) {
        if (!availabilityChecker.get()) return;

        session.writeTransaction(transaction -> {
            for (OWLOntologyChange change : changes) {
                String query = null;

                if (change.isAddAxiom()) {
                    query = handleAxiomByType(change.getAxiom(), true);
                } else if (change.isRemoveAxiom()) {
                    query = handleAxiomByType(change.getAxiom(), false);
                }

                if (query != null) {
                    transaction.run(query);
                } else {
                    logger.info("Unhandled axiom type {}", change.getAxiom().getAxiomType());
                }
            }
            return null;
        });
    }

    private String handleAxiomByType(OWLAxiom axiom, boolean isAdd) {
        AxiomType<?> type = axiom.getAxiomType();

        logger.info("Handle axiom type: {}", type);

        if (type == AxiomType.DECLARATION) {
            return handleDeclarationAxiom((OWLDeclarationAxiom) axiom, isAdd);
        } else if (type == AxiomType.SUBCLASS_OF) {
            return handleSubClassAxiom((OWLSubClassOfAxiom) axiom, isAdd);
        } else if (type == AxiomType.EQUIVALENT_CLASSES) {
            return handleEquivalentClassesAxiom((OWLEquivalentClassesAxiom) axiom, isAdd);
        } else if (type == AxiomType.DISJOINT_CLASSES) {
            return handleDisjointClassesAxiom((OWLDisjointClassesAxiom) axiom, isAdd);
        } else if (type == AxiomType.OBJECT_PROPERTY_DOMAIN) {
            return handleObjectPropertyDomainAxiom((OWLObjectPropertyDomainAxiom) axiom, isAdd);
        } else if (type == AxiomType.OBJECT_PROPERTY_RANGE) {
            return handleObjectPropertyRangeAxiom((OWLObjectPropertyRangeAxiom) axiom, isAdd);
        } else if (type == AxiomType.SUB_OBJECT_PROPERTY) {
            return handleSubObjectPropertyAxiom((OWLSubObjectPropertyOfAxiom) axiom, isAdd);
        } else if (type == AxiomType.EQUIVALENT_OBJECT_PROPERTIES) {
            return handleEquivalentObjectPropertiesAxiom((OWLEquivalentObjectPropertiesAxiom) axiom, isAdd);
        } else if (type == AxiomType.DISJOINT_OBJECT_PROPERTIES) {
            return handleDisjointObjectPropertiesAxiom((OWLDisjointObjectPropertiesAxiom) axiom, isAdd);
        } else if (type == AxiomType.DATA_PROPERTY_DOMAIN) {
            return handleDataPropertyDomainAxiom((OWLDataPropertyDomainAxiom) axiom, isAdd);
        } else if (type == AxiomType.DATA_PROPERTY_RANGE) {
            return handleDataPropertyRangeAxiom((OWLDataPropertyRangeAxiom) axiom, isAdd);
        } else if (type == AxiomType.SUB_DATA_PROPERTY) {
            return handleSubDataPropertyAxiom((OWLSubDataPropertyOfAxiom) axiom, isAdd);
        } else if (type == AxiomType.EQUIVALENT_DATA_PROPERTIES) {
            return handleEquivalentDataPropertiesAxiom((OWLEquivalentDataPropertiesAxiom) axiom, isAdd);
        } else if (type == AxiomType.DISJOINT_DATA_PROPERTIES) {
            return handleDisjointDataPropertiesAxiom((OWLDisjointDataPropertiesAxiom) axiom, isAdd);
        } else if (type == AxiomType.CLASS_ASSERTION) {
            return handleClassAssertionAxiom((OWLClassAssertionAxiom) axiom, isAdd);
        } else if (type == AxiomType.OBJECT_PROPERTY_ASSERTION) {
            return handleObjectPropertyAssertionAxiom((OWLObjectPropertyAssertionAxiom) axiom, isAdd);
        } else if (type == AxiomType.DATA_PROPERTY_ASSERTION) {
            return handleDataPropertyAssertionAxiom((OWLDataPropertyAssertionAxiom) axiom, isAdd);
        } else if (type == AxiomType.NEGATIVE_OBJECT_PROPERTY_ASSERTION) {
            return handleNegativeObjectPropertyAssertionAxiom((OWLNegativeObjectPropertyAssertionAxiom) axiom, isAdd);
        } else if (type == AxiomType.NEGATIVE_DATA_PROPERTY_ASSERTION) {
            return handleNegativeDataPropertyAssertionAxiom((OWLNegativeDataPropertyAssertionAxiom) axiom, isAdd);
        } else if (type == AxiomType.SAME_INDIVIDUAL) {
            return handleSameIndividualAxiom((OWLSameIndividualAxiom) axiom, isAdd);
        } else if (type == AxiomType.DIFFERENT_INDIVIDUALS) {
            return handleDifferentIndividualsAxiom((OWLDifferentIndividualsAxiom) axiom, isAdd);
        }

        return null;
    }

    private String handleDeclarationAxiom(OWLDeclarationAxiom axiom, boolean isAdd) {
        OWLEntity entity = axiom.getEntity();

        if (entity instanceof OWLClass) {
            return handleClassDeclaration((OWLClass) entity, isAdd);
        } else if (entity instanceof OWLObjectProperty) {
            return handleObjectPropertyDeclaration((OWLObjectProperty) entity, isAdd);
        } else if (entity instanceof OWLDataProperty) {
            return handleDataPropertyDeclaration((OWLDataProperty) entity, isAdd);
        } else if (entity instanceof OWLNamedIndividual) {
            return handleIndividualDeclaration((OWLNamedIndividual) entity, isAdd);
        }

        return null;
    }

    private String handleClassDeclaration(OWLClass cls, boolean isAdd) {
        String className = getShortForm(cls.getIRI());
        Map<String, String> props = new HashMap<>();
        props.put("name", className);

        String message = mapToJson(new ElementEvent("Class", props, isAdd));
        httpClient.sendMessage(message);

        return isAdd ? String.format("MERGE (c:Class {name: '%s'})", className) : String.format("MATCH (c:Class {name: '%s'}) DETACH DELETE c", className);
    }

    private String handleObjectPropertyDeclaration(OWLObjectProperty property, boolean isAdd) {
        String propName = getShortForm(property.getIRI());
        Map<String, String> props = new HashMap<>();
        props.put("name", propName);

        String message = mapToJson(new ElementEvent("ObjectProperty", props, isAdd));
        httpClient.sendMessage(message);

        return isAdd ? String.format("MERGE (op:ObjectProperty {name: '%s'})", propName) : String.format("MATCH (op:ObjectProperty {name: '%s'}) DETACH DELETE op", propName);
    }

    private String handleDataPropertyDeclaration(OWLDataProperty property, boolean isAdd) {
        String propName = getShortForm(property.getIRI());
        Map<String, String> props = new HashMap<>();
        props.put("name", propName);

        String message = mapToJson(new ElementEvent("DataProperty", props, isAdd));
        httpClient.sendMessage(message);

        return isAdd ? String.format("MERGE (dp:DataProperty {name: '%s'})", propName) : String.format("MATCH (dp:DataProperty {name: '%s'}) DETACH DELETE dp", propName);
    }

    private String handleIndividualDeclaration(OWLNamedIndividual individual, boolean isAdd) {
        String indName = getShortForm(individual.getIRI());
        Map<String, String> props = new HashMap<>();
        props.put("name", indName);

        String message = mapToJson(new ElementEvent("Individual", props, isAdd));
        httpClient.sendMessage(message);

        return isAdd ? String.format("MERGE (i:Individual {name: '%s'})", indName) : String.format("MATCH (i:Individual {name: '%s'}) DETACH DELETE i", indName);
    }

    private String handleSubClassAxiom(OWLSubClassOfAxiom axiom, boolean isAdd) {
        if (!isOWLClass(axiom.getSubClass()) || !isOWLClass(axiom.getSuperClass())) {
            return null;
        }

        String subClass = getShortForm(axiom.getSubClass().asOWLClass().getIRI());
        String superClass = getShortForm(axiom.getSuperClass().asOWLClass().getIRI());

        return isAdd ? String.format("MATCH (sub:Class {name: '%s'}), (super:Class {name: '%s'}) " + "MERGE (sub)-[:SubClassOf]->(super)", subClass, superClass) : String.format("MATCH (sub:Class {name: '%s'})-[r:SubClassOf]->(super:Class {name: '%s'}) " + "DELETE r", subClass, superClass);
    }

    private String handleEquivalentClassesAxiom(OWLEquivalentClassesAxiom axiom, boolean isAdd) {
        StringBuilder query = new StringBuilder();
        List<OWLClass> classes = axiom.getClassExpressions().stream().filter(this::isOWLClass).map(OWLClassExpression::asOWLClass).collect(Collectors.toList());

        for (int i = 0; i < classes.size(); i++) {
            for (int j = i + 1; j < classes.size(); j++) {
                String name1 = getShortForm(classes.get(i).getIRI());
                String name2 = getShortForm(classes.get(j).getIRI());

                query.append(isAdd ? String.format("MATCH (c1:Class {name: '%s'}), (c2:Class {name: '%s'}) " + "MERGE (c1)-[:EquivalentTo]-(c2); ", name1, name2) : String.format("MATCH (c1:Class {name: '%s'})-[r:EquivalentTo]-(c2:Class {name: '%s'}) " + "DELETE r; ", name1, name2));
            }
        }

        return query.toString();
    }

    private String handleDisjointClassesAxiom(OWLDisjointClassesAxiom axiom, boolean isAdd) {
        StringBuilder query = new StringBuilder();
        List<OWLClass> classes = axiom.getClassExpressions().stream().filter(this::isOWLClass).map(OWLClassExpression::asOWLClass).collect(Collectors.toList());

        for (int i = 0; i < classes.size(); i++) {
            for (int j = i + 1; j < classes.size(); j++) {
                String name1 = getShortForm(classes.get(i).getIRI());
                String name2 = getShortForm(classes.get(j).getIRI());

                query.append(isAdd ? String.format("MATCH (c1:Class {name: '%s'}), (c2:Class {name: '%s'}) " + "MERGE (c1)-[:DisjointWith]-(c2); ", name1, name2) : String.format("MATCH (c1:Class {name: '%s'})-[r:DisjointWith]-(c2:Class {name: '%s'}) " + "DELETE r; ", name1, name2));
            }
        }

        return query.toString();
    }

    private String handleObjectPropertyDomainAxiom(OWLObjectPropertyDomainAxiom axiom, boolean isAdd) {
        if (!axiom.getProperty().isNamed()) {
            return null;
        }

        String propertyName = getShortForm(axiom.getProperty().asOWLObjectProperty().getIRI());
        OWLClassExpression domain = axiom.getDomain();

        if (domain.isOWLThing()) {
            return isAdd ? String.format("MATCH (op:ObjectProperty {name: '%s'}) " + "REMOVE op.domain;", propertyName) : "";
        }

        if (!isOWLClass(domain)) {
            return null;
        }

        String domainName = getShortForm(domain.asOWLClass().getIRI());

        return String.format("MATCH (op:ObjectProperty {name: '%s'}), (d:Class {name: '%s'}) " + "%s (op)-[:Domain]->(d)", propertyName, domainName, isAdd ? "MERGE" : "MATCH (op)-[r:Domain]->(d) DELETE r");
    }

    private String handleObjectPropertyRangeAxiom(OWLObjectPropertyRangeAxiom axiom, boolean isAdd) {
        if (!axiom.getProperty().isNamed()) {
            return null;
        }

        String propertyName = getShortForm(axiom.getProperty().asOWLObjectProperty().getIRI());
        OWLClassExpression range = axiom.getRange();

        if (range.isOWLThing()) {
            return isAdd ? String.format("MATCH (op:ObjectProperty {name: '%s'}) " + "REMOVE op.range;", propertyName) : "";
        }

        if (!isOWLClass(range)) {
            return null;
        }

        String rangeName = getShortForm(range.asOWLClass().getIRI());

        return isAdd ? String.format("MATCH (op:ObjectProperty {name: '%s'}), (r:Class {name: '%s'}) " + "MERGE (op)-[:Range]->(r)", propertyName, rangeName) : String.format("MATCH (op:ObjectProperty {name: '%s'})-[rel:Range]->(r:Class {name: '%s'}) " + "DELETE rel", propertyName, rangeName);
    }

    private String handleSubObjectPropertyAxiom(OWLSubObjectPropertyOfAxiom axiom, boolean isAdd) {
        if (!axiom.getSubProperty().isNamed() || !axiom.getSuperProperty().isNamed()) {
            return null;
        }

        String subProp = getShortForm(axiom.getSubProperty().asOWLObjectProperty().getIRI());
        String superProp = getShortForm(axiom.getSuperProperty().asOWLObjectProperty().getIRI());

        return isAdd ? String.format("MATCH (sub:ObjectProperty {name: '%s'}), (super:ObjectProperty {name: '%s'}) " + "MERGE (sub)-[:SubPropertyOf]->(super)", subProp, superProp) : String.format("MATCH (sub:ObjectProperty {name: '%s'})-[r:SubPropertyOf]->(super:ObjectProperty {name: '%s'}) " + "DELETE r", subProp, superProp);
    }

    private String handleInversePropertiesAxiom(OWLInverseObjectPropertiesAxiom axiom, boolean isAdd) {
        if (!axiom.getFirstProperty().isNamed() || !axiom.getSecondProperty().isNamed()) {
            return null;
        }

        String prop1 = getShortForm(axiom.getFirstProperty().asOWLObjectProperty().getIRI());
        String prop2 = getShortForm(axiom.getSecondProperty().asOWLObjectProperty().getIRI());

        return isAdd ? String.format("MATCH (p1:ObjectProperty {name: '%s'}), (p2:ObjectProperty {name: '%s'}) " + "MERGE (p1)-[:InverseOf]->(p2) " + "MERGE (p2)-[:InverseOf]->(p1)", prop1, prop2) : String.format("MATCH (p1:ObjectProperty {name: '%s'})-[r1:InverseOf]->(p2:ObjectProperty {name: '%s'}) " + "MATCH (p2)-[r2:InverseOf]->(p1) " + "DELETE r1, r2", prop1, prop2);
    }

    private String handleEquivalentObjectPropertiesAxiom(OWLEquivalentObjectPropertiesAxiom axiom, boolean isAdd) {
        List<OWLObjectPropertyExpression> props = new ArrayList<>();
        axiom.getProperties().forEach(prop -> {
            if (prop.isNamed()) {
                props.add(prop);
            }
        });

        if (props.size() < 2) {
            return null;
        }

        StringBuilder query = new StringBuilder();
        for (int i = 0; i < props.size(); i++) {
            for (int j = i + 1; j < props.size(); j++) {
                String name1 = getShortForm(props.get(i).asOWLObjectProperty().getIRI());
                String name2 = getShortForm(props.get(j).asOWLObjectProperty().getIRI());

                query.append(isAdd ? String.format("MATCH (p1:ObjectProperty {name: '%s'}), (p2:ObjectProperty {name: '%s'}) " + "MERGE (p1)-[:EquivalentTo]-(p2); ", name1, name2) : String.format("MATCH (p1:ObjectProperty {name: '%s'})-[r:EquivalentTo]-(p2:ObjectProperty {name: '%s'}) " + "DELETE r; ", name1, name2));
            }
        }
        return query.toString();
    }

    private String handleDisjointObjectPropertiesAxiom(OWLDisjointObjectPropertiesAxiom axiom, boolean isAdd) {
        List<OWLObjectPropertyExpression> props = new ArrayList<>();
        axiom.getProperties().forEach(prop -> {
            if (prop.isNamed()) {
                props.add(prop);
            }
        });

        if (props.size() < 2) {
            return null;
        }

        StringBuilder query = new StringBuilder();
        for (int i = 0; i < props.size(); i++) {
            for (int j = i + 1; j < props.size(); j++) {
                String name1 = getShortForm(props.get(i).asOWLObjectProperty().getIRI());
                String name2 = getShortForm(props.get(j).asOWLObjectProperty().getIRI());

                query.append(isAdd ? String.format("MATCH (p1:ObjectProperty {name: '%s'}), (p2:ObjectProperty {name: '%s'}) " + "MERGE (p1)-[:DisjointWith]->(p2); ", name1, name2) : String.format("MATCH (p1:ObjectProperty {name: '%s'})-[r:DisjointWith]->(p2:ObjectProperty {name: '%s'}) " + "DELETE r; ", name1, name2));
            }
        }
        return query.toString();
    }

    private String handleDataPropertyDomainAxiom(OWLDataPropertyDomainAxiom axiom, boolean isAdd) {
        if (!axiom.getProperty().isNamed()) {
            return null;
        }

        String propName = getShortForm(axiom.getProperty().asOWLDataProperty().getIRI());
        OWLClassExpression domain = axiom.getDomain();

        if (!isOWLClass(domain)) {
            return null;
        }

        String domainName = getShortForm(domain.asOWLClass().getIRI());

        return isAdd ? String.format("MATCH (dp:DataProperty {name: '%s'}), (d:Class {name: '%s'}) " + "MERGE (dp)-[:Domain]->(d)", propName, domainName) : String.format("MATCH (dp:DataProperty {name: '%s'})-[r:Domain]->(d:Class {name: '%s'}) " + "DELETE r", propName, domainName);
    }

    private String handleDataPropertyRangeAxiom(OWLDataPropertyRangeAxiom axiom, boolean isAdd) {
        if (!axiom.getProperty().isNamed()) {
            return null;
        }

        String propName = getShortForm(axiom.getProperty().asOWLDataProperty().getIRI());
        OWLDataRange range = axiom.getRange();

        if (!range.isDatatype()) {
            return null;
        }

        String rangeName = range.asOWLDatatype().getIRI().getShortForm();

        return isAdd ? String.format("MATCH (dp:DataProperty {name: '%s'}) " + "SET dp.range = '%s'", propName, rangeName) : String.format("MATCH (dp:DataProperty {name: '%s'}) " + "REMOVE dp.range", propName);
    }

    private String handleSubDataPropertyAxiom(OWLSubDataPropertyOfAxiom axiom, boolean isAdd) {
        if (!axiom.getSubProperty().isNamed() || !axiom.getSuperProperty().isNamed()) {
            return null;
        }

        String subProp = getShortForm(axiom.getSubProperty().asOWLDataProperty().getIRI());
        String superProp = getShortForm(axiom.getSuperProperty().asOWLDataProperty().getIRI());

        return isAdd ? String.format("MATCH (sub:DataProperty {name: '%s'}), (super:DataProperty {name: '%s'}) " + "MERGE (sub)-[:SubPropertyOf]->(super)", subProp, superProp) : String.format("MATCH (sub:DataProperty {name: '%s'})-[r:SubPropertyOf]->(super:DataProperty {name: '%s'}) " + "DELETE r", subProp, superProp);
    }

    private String handleEquivalentDataPropertiesAxiom(OWLEquivalentDataPropertiesAxiom axiom, boolean isAdd) {
        List<OWLDataPropertyExpression> props = new ArrayList<>();
        axiom.getProperties().forEach(prop -> {
            if (prop.isNamed()) {
                props.add(prop);
            }
        });

        if (props.size() < 2) {
            return null;
        }

        StringBuilder query = new StringBuilder();
        for (int i = 0; i < props.size(); i++) {
            for (int j = i + 1; j < props.size(); j++) {
                String name1 = getShortForm(props.get(i).asOWLDataProperty().getIRI());
                String name2 = getShortForm(props.get(j).asOWLDataProperty().getIRI());

                query.append(isAdd ? String.format("MATCH (p1:DataProperty {name: '%s'}), (p2:DataProperty {name: '%s'}) " + "MERGE (p1)-[:EquivalentTo]-(p2); ", name1, name2) : String.format("MATCH (p1:DataProperty {name: '%s'})-[r:EquivalentTo]-(p2:DataProperty {name: '%s'}) " + "DELETE r; ", name1, name2));
            }
        }
        return query.toString();
    }

    private String handleDisjointDataPropertiesAxiom(OWLDisjointDataPropertiesAxiom axiom, boolean isAdd) {
        List<OWLDataPropertyExpression> props = new ArrayList<>();
        axiom.getProperties().forEach(prop -> {
            if (prop.isNamed()) {
                props.add(prop);
            }
        });

        if (props.size() < 2) {
            return null;
        }

        StringBuilder query = new StringBuilder();
        for (int i = 0; i < props.size(); i++) {
            for (int j = i + 1; j < props.size(); j++) {
                String name1 = getShortForm(props.get(i).asOWLDataProperty().getIRI());
                String name2 = getShortForm(props.get(j).asOWLDataProperty().getIRI());

                query.append(isAdd ? String.format("MATCH (p1:DataProperty {name: '%s'}), (p2:DataProperty {name: '%s'}) " + "MERGE (p1)-[:DisjointWith]->(p2); ", name1, name2) : String.format("MATCH (p1:DataProperty {name: '%s'})-[r:DisjointWith]->(p2:DataProperty {name: '%s'}) " + "DELETE r; ", name1, name2));
            }
        }
        return query.toString();
    }

    private String handleClassAssertionAxiom(OWLClassAssertionAxiom axiom, boolean isAdd) {
        if (!axiom.getIndividual().isNamed() || !isOWLClass(axiom.getClassExpression())) {
            return null;
        }

        String individual = getShortForm(axiom.getIndividual().asOWLNamedIndividual().getIRI());
        String className = getShortForm(axiom.getClassExpression().asOWLClass().getIRI());

        return isAdd ? String.format("MATCH (i:Individual {name: '%s'}), (c:Class {name: '%s'}) " + "MERGE (i)-[:TypeOf]->(c)", individual, className) : String.format("MATCH (i:Individual {name: '%s'})-[r:TypeOf]->(c:Class {name: '%s'}) " + "DELETE r", individual, className);
    }

    private String handleObjectPropertyAssertionAxiom(OWLObjectPropertyAssertionAxiom axiom, boolean isAdd) {
        if (!axiom.getProperty().isNamed() || !axiom.getSubject().isNamed() || !axiom.getObject().isNamed()) {
            return null;
        }

        String property = getShortForm(axiom.getProperty().asOWLObjectProperty().getIRI());
        String subject = getShortForm(axiom.getSubject().asOWLNamedIndividual().getIRI());
        String object = getShortForm(axiom.getObject().asOWLNamedIndividual().getIRI());

        return isAdd ? String.format("MATCH (s:Individual {name: '%s'}), (o:Individual {name: '%s'}) " + "MERGE (s)-[:%s]->(o)", subject, object, property) : String.format("MATCH (s:Individual {name: '%s'})-[r:%s]->(o:Individual {name: '%s'}) " + "DELETE r", subject, property, object);
    }

    private String handleDataPropertyAssertionAxiom(OWLDataPropertyAssertionAxiom axiom, boolean isAdd) {
        if (!axiom.getProperty().isNamed() || !axiom.getSubject().isNamed()) {
            return null;
        }

        String property = getShortForm(axiom.getProperty().asOWLDataProperty().getIRI());
        String individual = getShortForm(axiom.getSubject().asOWLNamedIndividual().getIRI());
        String value = escapeNeo4jValue(axiom.getObject().getLiteral());

        return isAdd ? String.format("MATCH (i:Individual {name: '%s'}) " + "MERGE (dp:DataProperty {name: '%s'}) " + "MERGE (i)-[:HasDataProperty]->(dp) " + "SET i.%s = '%s'", individual, property, property, value) : String.format("MATCH (i:Individual {name: '%s'})-[r:HasDataProperty]->" + "(dp:DataProperty {name: '%s'}) " + "DELETE r " + "REMOVE i.%s", individual, property, property);
    }

    private String handleNegativeObjectPropertyAssertionAxiom(OWLNegativeObjectPropertyAssertionAxiom axiom, boolean isAdd) {
        if (!axiom.getProperty().isNamed() || !axiom.getSubject().isNamed() || !axiom.getObject().isNamed()) {
            return null;
        }

        String property = getShortForm(axiom.getProperty().asOWLObjectProperty().getIRI());
        String subject = getShortForm(axiom.getSubject().asOWLNamedIndividual().getIRI());
        String object = getShortForm(axiom.getObject().asOWLNamedIndividual().getIRI());

        return isAdd ? String.format("MATCH (s:Individual {name: '%s'}), (o:Individual {name: '%s'}) " + "MERGE (s)-[:Not_%s]->(o)", subject, object, property) : String.format("MATCH (s:Individual {name: '%s'})-[r:Not_%s]->(o:Individual {name: '%s'}) " + "DELETE r", subject, property, object);
    }

    private String handleNegativeDataPropertyAssertionAxiom(OWLNegativeDataPropertyAssertionAxiom axiom, boolean isAdd) {
        if (!axiom.getProperty().isNamed() || !axiom.getSubject().isNamed()) {
            return null;
        }

        String property = getShortForm(axiom.getProperty().asOWLDataProperty().getIRI());
        String individual = getShortForm(axiom.getSubject().asOWLNamedIndividual().getIRI());
        String value = escapeNeo4jValue(axiom.getObject().getLiteral());

        return isAdd ? String.format("MATCH (i:Individual {name: '%s'}) " + "MERGE (dp:DataProperty {name: '%s'}) " + "MERGE (i)-[:HasNegativeDataProperty]->(dp) " + "SET i.not_%s = '%s'", individual, property, property, value) : String.format("MATCH (i:Individual {name: '%s'})-[r:HasNegativeDataProperty]->" + "(dp:DataProperty {name: '%s'}) " + "DELETE r " + "REMOVE i.not_%s", individual, property, property);
    }

    private String handleSameIndividualAxiom(OWLSameIndividualAxiom axiom, boolean isAdd) {
        List<OWLIndividual> individuals = new ArrayList<>();
        axiom.getIndividuals().forEach(ind -> {
            if (ind.isNamed()) {
                individuals.add(ind);
            }
        });

        if (individuals.size() < 2) {
            return null;
        }

        StringBuilder query = new StringBuilder();
        for (int i = 0; i < individuals.size(); i++) {
            for (int j = i + 1; j < individuals.size(); j++) {
                String name1 = getShortForm(individuals.get(i).asOWLNamedIndividual().getIRI());
                String name2 = getShortForm(individuals.get(j).asOWLNamedIndividual().getIRI());

                query.append(isAdd ? String.format("MATCH (i1:Individual {name: '%s'}), (i2:Individual {name: '%s'}) " + "MERGE (i1)-[:SameAs]->(i2); ", name1, name2) : String.format("MATCH (i1:Individual {name: '%s'})-[r:SameAs]->(i2:Individual {name: '%s'}) " + "DELETE r; ", name1, name2));
            }
        }
        return query.toString();
    }

    private String handleDifferentIndividualsAxiom(OWLDifferentIndividualsAxiom axiom, boolean isAdd) {
        List<OWLIndividual> individuals = new ArrayList<>();
        axiom.getIndividuals().forEach(ind -> {
            if (ind.isNamed()) {
                individuals.add(ind);
            }
        });

        if (individuals.size() < 2) {
            return null;
        }

        StringBuilder query = new StringBuilder();
        for (int i = 0; i < individuals.size(); i++) {
            for (int j = i + 1; j < individuals.size(); j++) {
                String name1 = getShortForm(individuals.get(i).asOWLNamedIndividual().getIRI());
                String name2 = getShortForm(individuals.get(j).asOWLNamedIndividual().getIRI());

                query.append(isAdd ? String.format("MATCH (i1:Individual {name: '%s'}), (i2:Individual {name: '%s'}) " + "MERGE (i1)-[:DifferentFrom]->(i2); ", name1, name2) : String.format("MATCH (i1:Individual {name: '%s'})-[r:DifferentFrom]->(i2:Individual {name: '%s'}) " + "DELETE r; ", name1, name2));
            }
        }
        return query.toString();
    }

    private boolean isOWLClass(OWLClassExpression expression) {
        return !expression.isOWLThing() && !expression.isOWLNothing();
    }

    private String getShortForm(IRI iri) {
        String iriString = iri.toString();
        int lastIndex = iriString.lastIndexOf('#');

        if (lastIndex == -1) {
            lastIndex = iriString.lastIndexOf('/');
        }

        return lastIndex != -1 ? iriString.substring(lastIndex + 1) : iriString;
    }

    // Auxiliary method for screening values
    private String escapeNeo4jValue(String value) {
        return value.replace("'", "\\'");
    }

    private String mapToJson(ElementEvent event) {
        try {
            String json = objectMapper.writeValueAsString(event);
            logger.info("Serialized event: {}", json);
            return json;
        } catch (Exception e) {
            logger.error("Failed to serialize event", e);
        }
        return null;
    }
}
