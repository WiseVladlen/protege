package org.protege.editor.owl.neo4j;

import org.semanticweb.owlapi.model.*;

public class EventProcessor {
    private final OWLOntologyManager manager;
    private final OWLOntology ontology;
    private final OWLDataFactory factory;

    public EventProcessor(OWLOntologyManager manager, OWLOntology ontology) {
        this.manager = manager;
        this.ontology = ontology;
        this.factory = manager.getOWLDataFactory();
    }

    public void process(ElementEvent event) {
        IRI iri = ontology.getOntologyID().getDefaultDocumentIRI().get();

        OWLAxiom axiom = null;

        switch (event.type) {
            case "Class":
                String className = event.props.get("name");
                OWLClass owlClass = factory.getOWLClass(IRI.create(iri + "#" + className));
                axiom = factory.getOWLDeclarationAxiom(owlClass);
                break;
            case "ObjectProperty":
                String objPropName = event.props.get("name");
                OWLObjectProperty objProp = factory.getOWLObjectProperty(IRI.create(iri + "#" + objPropName));
                axiom = factory.getOWLDeclarationAxiom(objProp);
                break;
            case "DataProperty":
                String dataPropName = event.props.get("name");
                OWLDataProperty dataProp = factory.getOWLDataProperty(IRI.create(iri + "#" + dataPropName));
                axiom = factory.getOWLDeclarationAxiom(dataProp);
                break;
            case "Individual":
                String individualName = event.props.get("name");
                OWLNamedIndividual individual = factory.getOWLNamedIndividual(IRI.create(iri + "#" + individualName));
                axiom = factory.getOWLDeclarationAxiom(individual);
                break;
            default:
                throw new IllegalArgumentException("Unsupported element type: " + event.type);
        }

        OWLOntologyChange change = event.isAdd
                ? new AddAxiom(ontology, axiom)
                : new RemoveAxiom(ontology, axiom);

        manager.applyChange(change);
    }
}

