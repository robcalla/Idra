/*******************************************************************************
 * Idra - Open Data Federation Platform
 *  Copyright (C) 2020 Engineering Ingegneria Informatica S.p.A.
 *  
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *  
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package it.eng.idra.dcat.dump;

import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.jena.datatypes.xsd.impl.XSDDateType;
import org.apache.jena.iri.IRI;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.ResourceRequiredException;
import org.apache.jena.riot.system.IRIResolver;
import org.apache.jena.shared.BadURIException;
import org.apache.jena.sparql.vocabulary.FOAF;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.apache.jena.vocabulary.VCARD4;

import it.eng.idra.beans.dcat.DCATDataset;
import it.eng.idra.beans.dcat.DCATDistribution;
import it.eng.idra.beans.dcat.DCATProperty;
import it.eng.idra.beans.dcat.DCTPeriodOfTime;
import it.eng.idra.beans.dcat.FOAFAgent;
import it.eng.idra.beans.dcat.VCardOrganization;
import it.eng.idra.beans.odms.ODMSCatalogue;
import it.eng.idra.utils.CommonUtil;

public class DCATAPITSerializer extends DCATAPSerializer {

	private static Property startDateProp = ResourceFactory.createProperty(DCATAP_IT_BASE_URI + "startDate");
	private static Property endDateProp = ResourceFactory.createProperty(DCATAP_IT_BASE_URI + "endDate");

	private DCATAPITSerializer() {
	}

	protected static Model addDatasetToModel(DCATDataset dataset, Model model) throws BadURIException {

		String landingPage = dataset.getLandingPage().getValue();
		IRI iri = iriFactory.create(landingPage);
		if (iri.hasViolation(false))
			throw new BadURIException(
					"URI for dataset: " + iri + "is not valid, skipping the dataset in the Jena Model:"
							+ (iri.violations(false).next()).getShortMessage());

		Resource datasetResource = model.createResource(iri.toString(),
				model.createResource(DCATAP_IT_BASE_URI + "Dataset"));

		datasetResource.addProperty(RDF.type, DCAT.Dataset);

		addDCATPropertyAsLiteral(dataset.getTitle(), datasetResource, model);

		addDCATPropertyAsLiteral(dataset.getDescription(), datasetResource, model);

		serializeConcept(dataset.getTheme(), model, datasetResource);

		serializeContactPoint(dataset.getContactPoint(), model, datasetResource);

		dataset.getKeywords().stream().filter(keyword -> StringUtils.isNotBlank(keyword))
				.forEach(keyword -> datasetResource.addLiteral(DCAT.keyword, keyword));

		serializeDCTStandard(dataset.getConformsTo(), datasetResource, model);

		List<DCATProperty> relatedResourceList = dataset.getRelatedResource();
		if (relatedResourceList != null)
			relatedResourceList.stream().filter(item -> isValidURI(item.getValue()))
					.forEach(item -> addDCATPropertyAsResource(item, datasetResource, model, true));

		serializeFOAFAgent(dataset.getRightsHolder(), model, datasetResource);

		serializeFOAFAgent(dataset.getCreator(), model, datasetResource);

		serializeFrequency(dataset.getFrequency(), model, datasetResource);

		List<DCATProperty> isVersionOf = dataset.getIsVersionOf();
		if (isVersionOf != null)
			isVersionOf.stream().filter(item -> isValidURI(item.getValue()))
					.forEach(item -> addDCATPropertyAsResource(item, datasetResource, model, true));

		addDCATPropertyAsResource(dataset.getLandingPage(), datasetResource, model, false);

		serializeLanguage(dataset.getLanguage(), model, datasetResource);

		datasetResource.addProperty(dataset.getReleaseDate().getProperty(), dataset.getReleaseDate().getValue(),
				XSDDateType.XSDdateTime);
		datasetResource.addProperty(dataset.getUpdateDate().getProperty(), dataset.getUpdateDate().getValue(),
				XSDDateType.XSDdateTime);
		datasetResource.addProperty(dataset.getIdentifier().getProperty(), dataset.getIdentifier().getValue());

		List<DCATProperty> otherIdentifier = dataset.getOtherIdentifier();
		if (otherIdentifier != null)
			otherIdentifier.stream().filter(id -> StringUtils.isNotBlank(id.getValue()))
					.forEach(id -> datasetResource.addProperty(model.createProperty(id.getProperty().getURI()),
							model.createResource(id.getRange()).addLiteral(SKOS.notation, id.getValue())));

		serializeSpatialCoverage(dataset.getSpatialCoverage(), model, datasetResource);

		serializeTemporalCoverage(dataset.getTemporalCoverage(), model, datasetResource);

		serializeConcept(dataset.getSubject(), model, datasetResource);

		List<DCATDistribution> distributions = dataset.getDistributions();
		for (DCATDistribution distribution : distributions) {
			addDistributionToModel(model, datasetResource, distribution);
		}

		ODMSCatalogue node = nodeResources.get(new Integer(dataset.getNodeID()));

		/*
		 * Add the Catalogue with the Dataset to the global Model
		 */
		model.createResource(node.getHost(), DCAT.Catalog).addLiteral(DCTerms.title, node.getName())
				.addLiteral(DCTerms.description, node.getDescription())// .addProperty(DCTerms.publisher, publisherNode)
				.addProperty(DCAT.dataset, datasetResource)
				.addProperty(DCTerms.issued, node.getRegisterDate().toString(), XSDDateType.XSDdateTime)
				.addProperty(DCTerms.modified, node.getLastUpdateDate().toString(), XSDDateType.XSDdateTime);

		/*
		 * ** Create the Publisher for the DCATCatalogue as a new FOAFAgent with info
		 * from the federated Catalogue
		 */

		String publisherResourceUri = node.getPublisherUrl();
		if (StringUtils.isBlank(publisherResourceUri) || !isValidURI(publisherResourceUri)) {

			if (!node.getHomepage().equalsIgnoreCase(node.getHost()))
				publisherResourceUri = node.getHomepage();
			else
				publisherResourceUri = node.getHomepage() + "/" + node.getId();
		}

		/*
		 * Check if the publisher is already present in the global Model and if any
		 * create it, either or both for dataset and catalog
		 */

		serializeFOAFAgent(
				new FOAFAgent(DCTerms.publisher.getURI(), publisherResourceUri, node.getPublisherName(),
						node.getPublisherEmail(), node.getPublisherUrl(), "", "", String.valueOf(node.getId())),
				model, model.getResource(node.getHost()));

		FOAFAgent datasetPublisher = dataset.getPublisher();
		if(datasetPublisher != null) {
			if (StringUtils.isNotBlank(datasetPublisher.getResourceUri()) && isValidURI(datasetPublisher.getResourceUri()))
				serializeFOAFAgent(datasetPublisher, model, datasetResource);
			else {
				// Set blank URI for Dataset Publisher in order to create a blank node
				datasetPublisher.setResourceUri("");
				serializeFOAFAgent(datasetPublisher, model, datasetResource);
			}
		}
		return model;

	}

	/**
	 * @param dataset
	 * @param model
	 * @param datasetResource
	 */
	private static void serializeContactPoint(List<VCardOrganization> contactPointList, Model model,
			Resource datasetResource) {

		if (contactPointList != null && !contactPointList.isEmpty())
			for (VCardOrganization contactPoint : contactPointList)
				try {
					Resource contactPointR = null;

					if (StringUtils.isNotBlank(contactPoint.getResourceUri()))
						contactPointR = model.createResource(contactPoint.getResourceUri(),
								model.createResource(DCATAP_IT_BASE_URI + "Organization"));
					else
						contactPointR = model.createResource(model.createResource(DCATAP_IT_BASE_URI + "Organization"));

					// Handle hasTelephone property
					// Resource telephoneResource = model.createResource(object.getURI() +
					// "#hasTelephone").addProperty(
					// contactPoint.getHasTelephoneValue().getProperty(),
					// contactPoint.getHasTelephoneValue().getValue());
					// addDCATPropertyAsResource(contactPoint.getHasTelephoneType(),
					// telephoneResource, model);

					// Add all properties to the Resource, that is the object of the
					// contactPoint property
					contactPointR.addProperty(RDF.type, VCARD4.Kind)
							.addProperty(RDF.type, VCardOrganization.getRDFClass())
							.addLiteral(contactPoint.getFn().getProperty(), contactPoint.getFn().getValue());
					// .addProperty(VCARD4.hasTelephone, telephoneResource);
					if (!contactPointR.hasProperty(VCARD4.hasTelephone)) {

						Resource hasTelephoneR = null;
						try {
							hasTelephoneR = model.createResource();

							addDCATPropertyAsLiteral(contactPoint.getHasTelephoneValue(), hasTelephoneR, model);

							try {
								addDCATPropertyAsResource(contactPoint.getHasTelephoneType(), hasTelephoneR, model,
										false);
							} catch (ResourceRequiredException e) {
								addDCATPropertyAsLiteral(contactPoint.getHasTelephoneType(), hasTelephoneR, model);

							}

							contactPointR.addProperty(VCARD4.hasTelephone, hasTelephoneR);

						} catch (ResourceRequiredException ignore) {
						}
					}

					// Fix hasEmail value if needed
					String hasEmailValue = contactPoint.getHasEmail().getValue();
					if (StringUtils.isNotBlank(hasEmailValue) && CommonUtil.checkIfIsEmail(hasEmailValue)) {
						if (!hasEmailValue.startsWith("mailto:"))
							contactPoint.getHasEmail().setValue("mailto:" + hasEmailValue);
						addDCATPropertyAsResource(contactPoint.getHasEmail(), contactPointR, model, false);
					}

					addDCATPropertyAsResource(contactPoint.getHasURL(), contactPointR, model, false);

					// Add contactPoint property to the dataset Resource;
					datasetResource.addProperty(model.createProperty(contactPoint.getPropertyUri()), contactPointR);

				} catch (Exception ignore) {
					logger.error(ignore);
				}

	}

	/**
	 * @param agent
	 * @param model
	 * @param parentResource
	 */
	protected static void serializeFOAFAgent(FOAFAgent agent, Model model, Resource parentResource) {
		if (agent != null) {

			Resource agentResource = null;

			if (StringUtils.isNotBlank(agent.getResourceUri()))
				agentResource = model.createResource(agent.getResourceUri(),
						model.createResource(DCATAP_IT_BASE_URI + "Agent"));
			else
				agentResource = model.createResource(DCATAP_IT_BASE_URI + "Agent");

			addDCATPropertyAsLiteral(agent.getName(), agentResource, model);
			addDCATPropertyAsLiteral(agent.getMbox(), agentResource, model);
			addDCATPropertyAsLiteral(agent.getType(), agentResource, model);
			addDCATPropertyAsLiteral(agent.getIdentifier(), agentResource, model);
			addDCATPropertyAsResource(agent.getHomepage(), agentResource, model, false);
			addDCATPropertyAsLiteral(new DCATProperty(RDF.type, null, FOAFAgent.getRDFClass().getURI()), agentResource,
					model);

			parentResource.addProperty(model.createProperty(agent.getPropertyUri()), agentResource);
		}

	}

	/**
	 * @param temporalCoverage
	 * @param model
	 * @param datasetResource
	 */
	protected static void serializeTemporalCoverage(DCTPeriodOfTime temporalCoverage, Model model,
			Resource datasetResource) {

		if (temporalCoverage != null)
			datasetResource.addProperty(model.createProperty(temporalCoverage.getUri()),
					model.createResource(DCTPeriodOfTime.getRDFClass())
							.addProperty(startDateProp, temporalCoverage.getStartDate().getValue(), XSDDateType.XSDdate)
							.addProperty(endDateProp, temporalCoverage.getEndDate().getValue(), XSDDateType.XSDdate));
	}

	/**
	 * @param model
	 * @param datasetResource
	 * @param distribution
	 */
	private static void addDistributionToModel(Model model, Resource datasetResource, DCATDistribution distribution) {

		Resource distResource = model.createResource(DCATDistribution.getRDFClass());

		addDCATPropertyAsResource(distribution.getAccessURL(), distResource, model, false);

		addDCATPropertyAsLiteral(distribution.getDescription(), distResource, model);

		serializeFormat(distribution.getFormat(), model, distResource);

		serializeLicense(distribution.getLicense(), model, distResource);

		addDCATPropertyAsLiteral(distribution.getByteSize(), distResource, model);

		addDCATPropertyAsResource(distribution.getDownloadURL(), distResource, model, false);

		distResource.addProperty(distribution.getUpdateDate().getProperty(), distribution.getUpdateDate().getValue(),
				XSDDateType.XSDdateTime);

		addDCATPropertyAsLiteral(distribution.getTitle(), distResource, model);

		datasetResource.addProperty(DCAT.distribution, distResource);
	}

}
