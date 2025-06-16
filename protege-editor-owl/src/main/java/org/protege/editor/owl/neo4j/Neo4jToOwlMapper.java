package org.protege.editor.owl.neo4j;


import org.neo4j.driver.Session;
import org.semanticweb.owlapi.model.*;

import java.util.*;

public class Neo4jToOwlMapper {
    public static void run(Session session, OWLOntologyManager manager, IRI iri) throws Exception {
        OWLOntology ontology = manager.getOntology(iri);
        if (ontology == null) {
            throw new Exception("Ontology not found");
        }

        List<OWLOntologyChange> changes = new ArrayList<>();

        Map<String, OWLClass> classMap = new HashMap<>();
        Map<String, OWLObjectProperty> objectPropertyMap = new HashMap<>();
        Map<String, OWLDataProperty> dataPropertyMap = new HashMap<>();
        Map<String, OWLNamedIndividual> individualMap = new HashMap<>();

        String ontologyIRI = iri.toString();
        OWLDataFactory dataFactory = manager.getOWLDataFactory();

        // Extract Classes
        session.run("MATCH (c:Class) RETURN c.name AS name").list().forEach(record -> {
            String className = record.get("name").asString();
            if (className.equals("Thing")) return;

            OWLClass owlClass = dataFactory.getOWLClass(IRI.create(ontologyIRI + "#" + className));
            changes.add(new AddAxiom(ontology, dataFactory.getOWLDeclarationAxiom(owlClass)));
            classMap.put(className, owlClass);
        });

        // Extract Object Properties
        session.run("MATCH (op:ObjectProperty) RETURN op.name AS name").list().forEach(record -> {
            String propertyName = record.get("name").asString();
            OWLObjectProperty objectProperty = dataFactory.getOWLObjectProperty(IRI.create(ontologyIRI + "#" + propertyName));
            changes.add(new AddAxiom(ontology, dataFactory.getOWLDeclarationAxiom(objectProperty)));
            objectPropertyMap.put(propertyName, objectProperty);
        });

        // Extract Data Properties
        session.run("MATCH (dp:DataProperty) RETURN dp.name AS name, dp.range AS range").list().forEach(record -> {
            String propertyName = record.get("name").asString();
            String range = record.get("range").asString(null); // Optional field

            OWLDataProperty dataProperty = dataFactory.getOWLDataProperty(IRI.create(ontologyIRI + "#" + propertyName));
            changes.add(new AddAxiom(ontology, dataFactory.getOWLDeclarationAxiom(dataProperty)));
            dataPropertyMap.put(propertyName, dataProperty);

            if (range != null) {
                OWLDatatype datatype = dataFactory.getOWLDatatype(getDataTypeIRI(range));
                OWLDataRange dataRange = dataFactory.getOWLDatatypeRestriction(datatype);
                OWLDataPropertyRangeAxiom rangeAxiom = dataFactory.getOWLDataPropertyRangeAxiom(dataProperty, dataRange);
                changes.add(new AddAxiom(ontology, rangeAxiom));
            }
        });

        // Extract SubClassOf relationships
        session.run("MATCH (sub:Class)-[:SubClassOf]->(sup:Class) RETURN sub.name AS sub, sup.name AS sup").list().forEach(record -> {
            String subClassName = record.get("sub").asString();
            String superClassName = record.get("sup").asString();

            OWLClass subClass = classMap.get(subClassName);
            OWLClass superClass = classMap.get(superClassName);

            if (subClass == null) return;

            if (Objects.equals(superClassName, "Thing")) {
                superClass = dataFactory.getOWLThing();
            }

            OWLSubClassOfAxiom axiom = dataFactory.getOWLSubClassOfAxiom(subClass, superClass);
            changes.add(new AddAxiom(ontology, axiom));
        });

        // Extract EquivalentTo relationships for classes
        session.run("MATCH (c1:Class)-[:EquivalentTo]-(c2:Class) RETURN c1.name AS class1, c2.name AS class2").list().forEach(record -> {
            String className1 = record.get("class1").asString();
            String className2 = record.get("class2").asString();

            OWLClass class1 = classMap.get(className1);
            OWLClass class2 = classMap.get(className2);

            if (class1 != null && class2 != null) {
                OWLEquivalentClassesAxiom axiom = dataFactory.getOWLEquivalentClassesAxiom(class1, class2);
                changes.add(new AddAxiom(ontology, axiom));
            }
        });

        // Extract DisjointWith relationships for classes
        session.run("MATCH (c1:Class)-[:DisjointWith]-(c2:Class) RETURN c1.name AS class1, c2.name AS class2").list().forEach(record -> {
            String className1 = record.get("class1").asString();
            String className2 = record.get("class2").asString();

            OWLClass class1 = classMap.get(className1);
            OWLClass class2 = classMap.get(className2);

            if (class1 != null && class2 != null) {
                OWLDisjointClassesAxiom axiom = dataFactory.getOWLDisjointClassesAxiom(class1, class2);
                changes.add(new AddAxiom(ontology, axiom));
            }
        });

        // Extract Domain relationships for Object Properties
        session.run("MATCH (op:ObjectProperty)-[:Domain]->(c:Class) RETURN op.name AS property, c.name AS domain").list().forEach(record -> {
            String propertyName = record.get("property").asString();
            String domainName = record.get("domain").asString();

            OWLObjectProperty objectProperty = objectPropertyMap.get(propertyName);
            OWLClass domainClass = classMap.get(domainName);

            if (objectProperty != null && domainClass != null) {
                OWLObjectPropertyDomainAxiom axiom = dataFactory.getOWLObjectPropertyDomainAxiom(objectProperty, domainClass);
                changes.add(new AddAxiom(ontology, axiom));
            }
        });

        // Extract Range relationships for Object Properties
        session.run("MATCH (op:ObjectProperty)-[:Range]->(c:Class) RETURN op.name AS property, c.name AS range").list().forEach(record -> {
            String propertyName = record.get("property").asString();
            String rangeName = record.get("range").asString();

            OWLObjectProperty objectProperty = objectPropertyMap.get(propertyName);
            OWLClass rangeClass = classMap.get(rangeName);

            if (objectProperty != null && rangeClass != null) {
                OWLObjectPropertyRangeAxiom axiom = dataFactory.getOWLObjectPropertyRangeAxiom(objectProperty, rangeClass);
                changes.add(new AddAxiom(ontology, axiom));
            }
        });

        // Extract SubPropertyOf relationships for Object Properties
        session.run("MATCH (sub:ObjectProperty)-[:SubPropertyOf]->(sup:ObjectProperty) RETURN sub.name AS sub, sup.name AS sup").list().forEach(record -> {
            String subPropName = record.get("sub").asString();
            String superPropName = record.get("sup").asString();

            OWLObjectProperty subProperty = objectPropertyMap.get(subPropName);
            OWLObjectProperty superProperty = objectPropertyMap.get(superPropName);

            if (subProperty != null && superProperty != null) {
                OWLSubObjectPropertyOfAxiom axiom = dataFactory.getOWLSubObjectPropertyOfAxiom(subProperty, superProperty);
                changes.add(new AddAxiom(ontology, axiom));
            }
        });

        // Extract InverseOf relationships for Object Properties
        session.run("MATCH (p1:ObjectProperty)-[:InverseOf]-(p2:ObjectProperty) RETURN p1.name AS prop1, p2.name AS prop2").list().forEach(record -> {
            String propName1 = record.get("prop1").asString();
            String propName2 = record.get("prop2").asString();

            OWLObjectProperty prop1 = objectPropertyMap.get(propName1);
            OWLObjectProperty prop2 = objectPropertyMap.get(propName2);

            if (prop1 != null && prop2 != null) {
                OWLInverseObjectPropertiesAxiom axiom = dataFactory.getOWLInverseObjectPropertiesAxiom(prop1, prop2);
                changes.add(new AddAxiom(ontology, axiom));
            }
        });

        // Extract EquivalentTo relationships for Object Properties
        session.run("MATCH (p1:ObjectProperty)-[:EquivalentTo]-(p2:ObjectProperty) RETURN p1.name AS prop1, p2.name AS prop2").list().forEach(record -> {
            String propName1 = record.get("prop1").asString();
            String propName2 = record.get("prop2").asString();

            OWLObjectProperty prop1 = objectPropertyMap.get(propName1);
            OWLObjectProperty prop2 = objectPropertyMap.get(propName2);

            if (prop1 != null && prop2 != null) {
                OWLEquivalentObjectPropertiesAxiom axiom = dataFactory.getOWLEquivalentObjectPropertiesAxiom(prop1, prop2);
                changes.add(new AddAxiom(ontology, axiom));
            }
        });

        // Extract DisjointWith relationships for Object Properties
        session.run("MATCH (p1:ObjectProperty)-[:DisjointWith]-(p2:ObjectProperty) RETURN p1.name AS prop1, p2.name AS prop2").list().forEach(record -> {
            String propName1 = record.get("prop1").asString();
            String propName2 = record.get("prop2").asString();

            OWLObjectProperty prop1 = objectPropertyMap.get(propName1);
            OWLObjectProperty prop2 = objectPropertyMap.get(propName2);

            if (prop1 != null && prop2 != null) {
                OWLDisjointObjectPropertiesAxiom axiom = dataFactory.getOWLDisjointObjectPropertiesAxiom(prop1, prop2);
                changes.add(new AddAxiom(ontology, axiom));
            }
        });

        // Extract Domain relationships for Data Properties
        session.run("MATCH (dp:DataProperty)-[:Domain]->(c:Class) RETURN dp.name AS property, c.name AS domain").list().forEach(record -> {
            String propertyName = record.get("property").asString();
            String domainName = record.get("domain").asString();

            OWLDataProperty dataProperty = dataPropertyMap.get(propertyName);
            OWLClass domainClass = classMap.get(domainName);

            if (dataProperty != null && domainClass != null) {
                OWLDataPropertyDomainAxiom axiom = dataFactory.getOWLDataPropertyDomainAxiom(dataProperty, domainClass);
                changes.add(new AddAxiom(ontology, axiom));
            }
        });

        // Extract SubPropertyOf relationships for Data Properties
        session.run("MATCH (sub:DataProperty)-[:SubPropertyOf]->(sup:DataProperty) RETURN sub.name AS sub, sup.name AS sup").list().forEach(record -> {
            String subPropName = record.get("sub").asString();
            String superPropName = record.get("sup").asString();

            OWLDataProperty subProperty = dataPropertyMap.get(subPropName);
            OWLDataProperty superProperty = dataPropertyMap.get(superPropName);

            if (subProperty != null && superProperty != null) {
                OWLSubDataPropertyOfAxiom axiom = dataFactory.getOWLSubDataPropertyOfAxiom(subProperty, superProperty);
                changes.add(new AddAxiom(ontology, axiom));
            }
        });

        // Extract EquivalentTo relationships for Data Properties
        session.run("MATCH (p1:DataProperty)-[:EquivalentTo]-(p2:DataProperty) RETURN p1.name AS prop1, p2.name AS prop2").list().forEach(record -> {
            String propName1 = record.get("prop1").asString();
            String propName2 = record.get("prop2").asString();

            OWLDataProperty prop1 = dataPropertyMap.get(propName1);
            OWLDataProperty prop2 = dataPropertyMap.get(propName2);

            if (prop1 != null && prop2 != null) {
                OWLEquivalentDataPropertiesAxiom axiom = dataFactory.getOWLEquivalentDataPropertiesAxiom(prop1, prop2);
                changes.add(new AddAxiom(ontology, axiom));
            }
        });

        // Extract DisjointWith relationships for Data Properties
        session.run("MATCH (p1:DataProperty)-[:DisjointWith]-(p2:DataProperty) RETURN p1.name AS prop1, p2.name AS prop2").list().forEach(record -> {
            String propName1 = record.get("prop1").asString();
            String propName2 = record.get("prop2").asString();

            OWLDataProperty prop1 = dataPropertyMap.get(propName1);
            OWLDataProperty prop2 = dataPropertyMap.get(propName2);

            if (prop1 != null && prop2 != null) {
                OWLDisjointDataPropertiesAxiom axiom = dataFactory.getOWLDisjointDataPropertiesAxiom(prop1, prop2);
                changes.add(new AddAxiom(ontology, axiom));
            }
        });

        // Extract Individuals and their relationships
        session.run("MATCH (i:Individual) RETURN i.name AS name").list().forEach(record -> {
            String individualName = record.get("name").asString();

            OWLNamedIndividual individual = dataFactory.getOWLNamedIndividual(IRI.create(ontologyIRI + "#" + individualName));
            changes.add(new AddAxiom(ontology, dataFactory.getOWLDeclarationAxiom(individual)));
            individualMap.put(individualName, individual);

            // Process class assertions for the individual
            session.run("MATCH (i:Individual {name: $name})-[:TypeOf]->(c:Class) RETURN c.name AS className", Collections.singletonMap("name", individualName)).list().forEach(classRecord -> {
                String className = classRecord.get("className").asString();
                OWLClass individualClass = classMap.computeIfAbsent(className, name -> {
                    OWLClass newClass = dataFactory.getOWLClass(IRI.create(ontologyIRI + "#" + name));
                    changes.add(new AddAxiom(ontology, dataFactory.getOWLDeclarationAxiom(newClass)));
                    return newClass;
                });
                changes.add(new AddAxiom(ontology, dataFactory.getOWLClassAssertionAxiom(individualClass, individual)));
            });

            // TODO: NOT WORKING
            // Process object property assertions
            session.run("MATCH (i:Individual {name: $name})-[r]->(target:Individual) RETURN type(r) AS type, target.name AS target", Collections.singletonMap("name", individualName)).list().forEach(propRecord -> {
                String propertyName = propRecord.get("type").asString();
                String targetName = propRecord.get("target").asString();

                if (propertyName.startsWith("Not_")) {
                    // Negative object property assertion
                    String actualPropertyName = propertyName.substring(4);
                    OWLObjectProperty objectProperty = objectPropertyMap.get(actualPropertyName);
                    OWLNamedIndividual target = individualMap.computeIfAbsent(targetName, name -> dataFactory.getOWLNamedIndividual(IRI.create(ontologyIRI + "#" + name)));

                    if (objectProperty != null) {
                        OWLNegativeObjectPropertyAssertionAxiom axiom = dataFactory.getOWLNegativeObjectPropertyAssertionAxiom(objectProperty, individual, target);
                        changes.add(new AddAxiom(ontology, axiom));
                    }
                } else {
                    // Positive object property assertion
                    OWLObjectProperty objectProperty = objectPropertyMap.get(propertyName);
                    OWLNamedIndividual target = individualMap.computeIfAbsent(targetName, name -> dataFactory.getOWLNamedIndividual(IRI.create(ontologyIRI + "#" + name)));

                    if (objectProperty != null) {
                        OWLObjectPropertyAssertionAxiom axiom = dataFactory.getOWLObjectPropertyAssertionAxiom(objectProperty, individual, target);
                        changes.add(new AddAxiom(ontology, axiom));
                    }
                }
            });

            // TODO: check later
            // Process sameIndividualAs relationships
            session.run("MATCH (i1:Individual {name: $name})-[:SameAs]-(i2:Individual) RETURN i2.name AS other", Collections.singletonMap("name", individualName)).list().forEach(sameRecord -> {
                String otherName = sameRecord.get("other").asString();

                OWLNamedIndividual other = individualMap.computeIfAbsent(otherName, name -> dataFactory.getOWLNamedIndividual(IRI.create(ontologyIRI + "#" + name)));
                changes.add(new AddAxiom(ontology, dataFactory.getOWLSameIndividualAxiom(individual, other)));
            });

            // TODO: check later
            // Process differentIndividuals relationships
            session.run("MATCH (i1:Individual {name: $name})-[:DifferentFrom]-(i2:Individual) RETURN i2.name AS other", Collections.singletonMap("name", individualName)).list().forEach(diffRecord -> {
                String otherName = diffRecord.get("other").asString();

                OWLNamedIndividual other = individualMap.computeIfAbsent(otherName, name -> dataFactory.getOWLNamedIndividual(IRI.create(ontologyIRI + "#" + name)));
                changes.add(new AddAxiom(ontology, dataFactory.getOWLDifferentIndividualsAxiom(individual, other)));
            });
        });

        // Extract Individuals and their data property assertions (batch processing)
        session.run("MATCH (i:Individual)-[:HasDataProperty]->(dp:DataProperty) RETURN i.name AS individual, dp.name AS property, i[dp.name] AS value").list().forEach(record -> {
            String individualName = record.get("individual").asString();
            String propertyName = record.get("property").asString();
            String propertyValue = record.get("value").asString();

            OWLNamedIndividual individual = individualMap.computeIfAbsent(individualName, name -> {
                OWLNamedIndividual ind = dataFactory.getOWLNamedIndividual(IRI.create(ontologyIRI + "#" + name));
                changes.add(new AddAxiom(ontology, dataFactory.getOWLDeclarationAxiom(ind)));
                return ind;
            });

            OWLDataProperty dataProperty = dataPropertyMap.computeIfAbsent(propertyName, name -> {
                OWLDataProperty prop = dataFactory.getOWLDataProperty(IRI.create(ontologyIRI + "#" + name));
                changes.add(new AddAxiom(ontology, dataFactory.getOWLDeclarationAxiom(prop)));
                return prop;
            });

            OWLLiteral literalValue = dataFactory.getOWLLiteral(propertyValue);
            OWLDataPropertyAssertionAxiom axiom = dataFactory.getOWLDataPropertyAssertionAxiom(dataProperty, individual, literalValue);
            changes.add(new AddAxiom(ontology, axiom));
        });

        // Extract negative data property assertions (batch processing)
        session.run("MATCH (i:Individual)-[:HasNegativeDataProperty]->(dp:DataProperty) RETURN i.name AS individual, dp.name AS property, i['not_' + dp.name] AS value").list().forEach(record -> {
            String individualName = record.get("individual").asString();
            String propertyName = record.get("property").asString();
            String propertyValue = record.get("value").asString();

            OWLNamedIndividual individual = individualMap.computeIfAbsent(individualName, name -> {
                OWLNamedIndividual ind = dataFactory.getOWLNamedIndividual(IRI.create(ontologyIRI + "#" + name));
                changes.add(new AddAxiom(ontology, dataFactory.getOWLDeclarationAxiom(ind)));
                return ind;
            });

            OWLDataProperty dataProperty = dataPropertyMap.computeIfAbsent(propertyName, name -> {
                OWLDataProperty prop = dataFactory.getOWLDataProperty(IRI.create(ontologyIRI + "#" + name));
                changes.add(new AddAxiom(ontology, dataFactory.getOWLDeclarationAxiom(prop)));
                return prop;
            });

            OWLLiteral literalValue = dataFactory.getOWLLiteral(propertyValue);
            OWLNegativeDataPropertyAssertionAxiom axiom = dataFactory.getOWLNegativeDataPropertyAssertionAxiom(dataProperty, individual, literalValue);
            changes.add(new AddAxiom(ontology, axiom));
        });

        // TODO: NOT WORKING
        // Extract object property assertions (batch processing)
        session.run("MATCH (source:Individual)-[r]->(target:Individual) WHERE NOT type(r) STARTS WITH 'Not_' RETURN source.name AS source, type(r) AS property, target.name AS target").list().forEach(record -> {
            String sourceName = record.get("source").asString();
            String propertyName = record.get("property").asString();
            String targetName = record.get("target").asString();

            OWLNamedIndividual source = individualMap.computeIfAbsent(sourceName, name -> {
                OWLNamedIndividual ind = dataFactory.getOWLNamedIndividual(IRI.create(ontologyIRI + "#" + name));
                changes.add(new AddAxiom(ontology, dataFactory.getOWLDeclarationAxiom(ind)));
                return ind;
            });

            OWLNamedIndividual target = individualMap.computeIfAbsent(targetName, name -> {
                OWLNamedIndividual ind = dataFactory.getOWLNamedIndividual(IRI.create(ontologyIRI + "#" + name));
                changes.add(new AddAxiom(ontology, dataFactory.getOWLDeclarationAxiom(ind)));
                return ind;
            });

            OWLObjectProperty objectProperty = objectPropertyMap.computeIfAbsent(propertyName, name -> {
                OWLObjectProperty prop = dataFactory.getOWLObjectProperty(IRI.create(ontologyIRI + "#" + name));
                changes.add(new AddAxiom(ontology, dataFactory.getOWLDeclarationAxiom(prop)));
                return prop;
            });

            OWLObjectPropertyAssertionAxiom axiom = dataFactory.getOWLObjectPropertyAssertionAxiom(objectProperty, source, target);
            changes.add(new AddAxiom(ontology, axiom));
        });

        // TODO: NOT WORKING
        // Extract negative object property assertions (batch processing)
        session.run("MATCH (source:Individual)-[r]->(target:Individual) WHERE type(r) STARTS WITH 'Not_' RETURN source.name AS source, substring(type(r), 4) AS property, target.name AS target").list().forEach(record -> {
            String sourceName = record.get("source").asString();
            String propertyName = record.get("property").asString();
            String targetName = record.get("target").asString();

            OWLNamedIndividual source = individualMap.computeIfAbsent(sourceName, name -> {
                OWLNamedIndividual ind = dataFactory.getOWLNamedIndividual(IRI.create(ontologyIRI + "#" + name));
                changes.add(new AddAxiom(ontology, dataFactory.getOWLDeclarationAxiom(ind)));
                return ind;
            });

            OWLNamedIndividual target = individualMap.computeIfAbsent(targetName, name -> {
                OWLNamedIndividual ind = dataFactory.getOWLNamedIndividual(IRI.create(ontologyIRI + "#" + name));
                changes.add(new AddAxiom(ontology, dataFactory.getOWLDeclarationAxiom(ind)));
                return ind;
            });

            OWLObjectProperty objectProperty = objectPropertyMap.computeIfAbsent(propertyName, name -> {
                OWLObjectProperty prop = dataFactory.getOWLObjectProperty(IRI.create(ontologyIRI + "#" + name));
                changes.add(new AddAxiom(ontology, dataFactory.getOWLDeclarationAxiom(prop)));
                return prop;
            });

            OWLNegativeObjectPropertyAssertionAxiom axiom = dataFactory.getOWLNegativeObjectPropertyAssertionAxiom(objectProperty, source, target);
            changes.add(new AddAxiom(ontology, axiom));
        });

        manager.applyChanges(changes);
    }

    private static IRI getDataTypeIRI(String value) {
        if (value.equals("real") || value.equals("rational")) {
            return IRI.create("http://www.w3.org/2002/07/owl#" + value);
        }
        return IRI.create("http://www.w3.org/2001/XMLSchema#" + value);
    }
}
