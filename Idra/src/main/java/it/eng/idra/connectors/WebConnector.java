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
package it.eng.idra.connectors;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.jena.vocabulary.DCAT;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.collect.Sets.SetView;

import it.eng.idra.beans.dcat.DCATDataset;
import it.eng.idra.beans.dcat.DCATDistribution;
import it.eng.idra.beans.dcat.DCTLocation;
import it.eng.idra.beans.dcat.DCTPeriodOfTime;
import it.eng.idra.beans.dcat.DCTStandard;
import it.eng.idra.beans.dcat.FOAFAgent;
import it.eng.idra.beans.dcat.SKOSConcept;
import it.eng.idra.beans.dcat.SKOSConceptSubject;
import it.eng.idra.beans.dcat.SKOSConceptTheme;
import it.eng.idra.beans.dcat.SKOSPrefLabel;
import it.eng.idra.beans.dcat.VCardOrganization;
import it.eng.idra.beans.exception.DatasetNotValidException;
import it.eng.idra.beans.odms.ODMSCatalogue;
import it.eng.idra.beans.odms.ODMSSynchronizationResult;
import it.eng.idra.beans.webscraper.DatasetSelector;
import it.eng.idra.beans.webscraper.WebScraperSelector;
import it.eng.idra.beans.webscraper.WebScraperSelectorType;
import it.eng.idra.connectors.webscraper.WebScraper;
import it.eng.idra.utils.CommonUtil;

public class WebConnector implements IODMSConnector {

	private String nodeID;
	private ODMSCatalogue node;
	private static Logger logger = LogManager.getLogger(WebConnector.class);

	private static Pattern staticPattern = Pattern.compile("(distribution_)(\\d)_(\\w+)");
	private static Pattern dinamicPattern = Pattern.compile("(distribution_)((?!((\\d+)_)).*)$");
	private static Pattern shiftPattern = Pattern.compile("div:nth-of-type\\((\\d+)\\)");
	private static Pattern downloadUrlPattern = Pattern.compile("\\w*\\(([^)]+)\\);*");

	public WebConnector() {
	}

	public WebConnector(ODMSCatalogue node) {
		this.node = node;
		this.nodeID = String.valueOf(node.getId());
	}

	// Live search is not available
	@Override
	public List<DCATDataset> findDatasets(HashMap<String, Object> searchParameters) throws Exception {
		ArrayList<DCATDataset> resultDatasets = new ArrayList<DCATDataset>();
		return resultDatasets;
	}

	@Override
	public int countSearchDatasets(HashMap<String, Object> searchParameters) throws Exception {
		return 0;
	}

	@Override
	public int countDatasets() throws Exception {
		// Return -1 in order to keep the node as ONLINE, but is not possible to
		// retrieve actual Datasets count from a WEB node
		try {
			Document doc = Jsoup.connect(node.getHost()).get();
			return (doc != null) ? -1 : 0;

		} catch (Exception e) {
			e.printStackTrace();
			return 0;
		}
	}

	@Override
	public DCATDataset datasetToDCAT(Object dataset, ODMSCatalogue node) throws DatasetNotValidException {

		DCATDataset mapped;
		List<DatasetSelector> selectors = new ArrayList<DatasetSelector>(),
				distrSelectors = new ArrayList<DatasetSelector>();

		String title = null, description = null, accessRights = null, frequency = null, landingPage = null,
				releaseDate = null, updateDate = null, identifier = null, type = null, version = null;

		String publisherIdentifier = null, publisherUri = null, publisherName = null, publisherMbox = null,
				publisherHomepage = null, publisherType = null;
		String holderIdentifier = null, holderUri = null, holderName = null, holderMbox = null, holderHomepage = null,
				holderType = null;
		String creatorIdentifier = null, creatorUri = null, creatorName = null, creatorMbox = null,
				creatorHomepage = null, creatorType = null;
		String conformsToName = null, conformsToIdentifier = null, conformsToTitle = null, conformsToDescription = null,
				conformsToReferenceDocumentation = null;
		String startDate = null, endDate = null;
		String vCardUri = null, vCardFn = null, vCardHasEmail = null, vCardHasTelephone = null, vCardHasURL = null;
		List<DCTStandard> conformsTo = new ArrayList<DCTStandard>();
		FOAFAgent publisher = null, rightsHolder = null, creator = null;
		List<VCardOrganization> contactPointList = new ArrayList<VCardOrganization>();
		DCTPeriodOfTime temporalCoverage = null;
		DCTLocation spatialCoverage = null;
		String geographicalIdentifier = null, geographicalName = null, geometry = null;
		List<SKOSConceptTheme> themeList = new ArrayList<SKOSConceptTheme>();
		List<SKOSConceptSubject> subjectList = null;
		List<String> keywords = new ArrayList<String>(), documentation = new ArrayList<String>(),
				hasVersion = new ArrayList<String>(), isVersionOf = new ArrayList<String>(),
				language = new ArrayList<String>(), provenance = new ArrayList<String>(),
				otherIdentifier = new ArrayList<String>(), sample = new ArrayList<String>(),
				source = new ArrayList<String>(), versionNotes = new ArrayList<String>(),
				subject = new ArrayList<String>(), relatedResource = new ArrayList<String>();
		List<DCATDistribution> distributionList = new ArrayList<DCATDistribution>();

		Document doc = (Document) dataset;

		/*
		 * Divide Dataset selectors from the ones related to the Distribution. Scrape
		 * first Distributions -> If they are > 1 -> Increments by one the N value of
		 * all the Dataset selectors of type
		 * "div:nth-of-type(N). In order to manage the DIV shifting caused by the Distribution divs"
		 */

		Map<Boolean, List<DatasetSelector>> partitions = node.getSitemap().getDatasetSelectors().stream()
				.collect(Collectors.partitioningBy(d -> d.getName().startsWith("distribution_")));
		distrSelectors.addAll(partitions.get(true));
		selectors.addAll(partitions.get(false));

		/*
		 * ** First fetch all the Distributions. Its number is used to manage a possible
		 * DIV shifting
		 */
		// DIV shifting
		distributionList.addAll(manageDistributionSelectors(distrSelectors, null, doc));
		int distrNumber = distributionList.size();
		int shift = distrNumber > 1 ? distrNumber - 1 : distrNumber == 0 ? -1 : 0;

		for (DatasetSelector selector : selectors) {

			/*
			 * ** Apply the shift if needed
			 */
			String cssSelector = selector.getSelector().replaceAll("'", "");
			if (shift != 0) {
				Matcher shiftMatcher = shiftPattern.matcher(cssSelector);
				String argument = null;
				if (shiftMatcher.find() && (argument = shiftMatcher.group(1)) != null) {
					int argValue = Integer.parseInt(argument);
					cssSelector = cssSelector.replace("(" + argValue + ")", "(" + (argValue + shift) + ")");
				}

			}

			/*
			 * ** Extract the values from the HTML document using the Dataset Selector
			 */

			List<String> extractedValues = fetchMultipleValuesBySelector(doc, selector);

			/*
			 * If there are extracted values, map them to the corresponding dataset field,
			 * otherwise go to the next selector iteration
			 */
			if (extractedValues.size() == 0)
				continue;

			switch (selector.getName()) {

			case "title":
				title = extractedValues.get(0);
				if (StringUtils.isBlank(title) || WebScraperSelector.getDefaultStopValues().contains(title)
						|| (selector.getStopValues() != null && selector.getStopValues().contains(title))) {
					throw new DatasetNotValidException("The value " + title + " for the selector: " + selector.getName()
							+ " is a stopValue or is empty then not valid and dataset with URL: " + doc.baseUri()
							+ " was skipped");
				}
				if ("Referente :".equals(title)) {
					throw new DatasetNotValidException("The value " + title + " for the selector: " + selector.getName()
							+ " is not a valid title since it is the next div label, dataset with URL: " + doc.baseUri()
							+ " was skipped");
				}
				break;
			case "description":
				description = extractedValues.get(0);
				break;
			case "publisher_name":
				publisherName = extractedValues.get(0);
				break;
			case "publisher_mbox":
				publisherMbox = extractedValues.get(0);
				break;
			case "publisher_homepage":
				publisherHomepage = extractedValues.get(0);
				break;
			case "publisher_type":
				publisherType = extractedValues.get(0);
				break;
			case "publisher_identifier":
				publisherIdentifier = extractedValues.get(0);
				break;
			case "publisher_uri":
				publisherUri = extractedValues.get(0);
				break;
			case "contact_fn":
				vCardFn = extractedValues.get(0);
				break;
			case "contact_email":
				vCardHasEmail = extractedValues.get(0);
				break;
			case "contact_telephone":
				vCardHasTelephone = extractedValues.get(0);
				break;
			case "contact_url":
				vCardHasURL = extractedValues.get(0);
				break;
			case "keywords":
				extractedValues.stream().filter(StringUtils::isNoneBlank)
						.forEach(x -> Arrays.stream(x.trim().split(",")).forEach(k -> keywords.add(k)));
				break;
			case "accessRights":
				accessRights = extractedValues.get(0);
				break;
			case "conformsTo_identifier":
				conformsToIdentifier = extractedValues.get(0);
				break;
			case "conformsTo_title":
				conformsToTitle = extractedValues.get(0);
				break;
			case "conformsTo_description":
				conformsToDescription = extractedValues.get(0);
				break;
			case "conformsTo_referenceDocumentation":
				conformsToReferenceDocumentation = extractedValues.get(0);
				break;
			case "documentation":
				documentation.addAll(extractedValues);
				break;
			case "frequency":
				frequency = extractedValues.get(0);
				break;
			case "hasVersion":
				hasVersion.add(extractedValues.get(0));
				break;
			case "isVersionOf":
				isVersionOf.add(extractedValues.get(0));
				break;
			case "landingPage":
				landingPage = extractedValues.get(0);
				break;
			case "language":
				language.add(extractedValues.get(0));
				break;
			case "provenance":
				provenance.add(extractedValues.get(0));
				break;
			case "releaseDate":
				try {
					releaseDate = CommonUtil.fromLocalToUtcDate(extractedValues.get(0), null);
				} catch (IllegalArgumentException ignore) {
				}
				break;
			case "updateDate":
				try {
					updateDate = CommonUtil.fromLocalToUtcDate(extractedValues.get(0), null);
				} catch (IllegalArgumentException ignore) {
				}
				break;
			case "source":
				source.add(extractedValues.get(0));
				break;
			case "sample":
				sample.add(extractedValues.get(0));
				break;
			case "spatialCoverage_geographicalIdentifier":
				geographicalIdentifier = extractedValues.get(0);
				break;
			case "spatialCoverage_geographicalName":
				geographicalName = extractedValues.get(0);
				break;
			case "spatialCoverage_geometry":
				geometry = extractedValues.get(0);
				break;
			case "temporalCoverage_startDate":
				try {
					startDate = CommonUtil.fromLocalToUtcDate(extractedValues.get(0), null);
				} catch (IllegalArgumentException ignore) {
				}
				break;
			case "temporalCoverage_endDate":
				try {
					endDate = CommonUtil.fromLocalToUtcDate(extractedValues.get(0), null);
				} catch (IllegalArgumentException ignore) {
				}
				break;
			case "type":
				type = extractedValues.get(0);
				break;
			case "version":
				version = extractedValues.get(0);
				break;
			case "versionNotes":
				versionNotes.add(extractedValues.get(0));
				break;
			case "rightsHolder_name":
				holderName = extractedValues.get(0);
				break;
			case "rightsHolder_mbox":
				holderMbox = extractedValues.get(0);
				break;
			case "rightsHolder_homepage":
				holderName = extractedValues.get(0);
				break;
			case "rightsHolder_type":
				holderType = extractedValues.get(0);
				break;
			case "rightsHolder_uri":
				holderUri = extractedValues.get(0);
				break;
			case "rightsHolder_identifier":
				holderIdentifier = extractedValues.get(0);
				break;
			case "creator_name":
				creatorName = extractedValues.get(0);
				break;
			case "creator_mbox":
				creatorMbox = extractedValues.get(0);
				break;
			case "creator_homepage":
				creatorHomepage = extractedValues.get(0);
				break;
			case "creator_type":
				creatorType = extractedValues.get(0);
				break;
			case "creator_uri":
				creatorUri = extractedValues.get(0);
				break;
			case "creator_identifier":
				creatorIdentifier = extractedValues.get(0);
				break;
			case "subject":
				subject.add(extractedValues.get(0));
				break;
			case "theme":
				themeList.addAll(extractConceptList(DCAT.theme.getURI(), extractedValues, SKOSConceptTheme.class));
				break;
			default:
				break;
			}

		}

		if (StringUtils.isNotBlank(geographicalIdentifier) || StringUtils.isNotBlank(geographicalName)
				|| StringUtils.isNotBlank(geometry))
			spatialCoverage = new DCTLocation(DCTerms.spatial.getURI(), geographicalIdentifier, geographicalName,
					geometry, nodeID);

		if (StringUtils.isNotBlank(startDate) && StringUtils.isNotBlank(endDate))
			temporalCoverage = new DCTPeriodOfTime(DCTerms.temporal.getURI(), startDate, endDate, nodeID);

		// Contact Point
		if (vCardUri != null || vCardFn != null || vCardHasEmail != null)
			contactPointList.add(new VCardOrganization(DCAT.contactPoint.getURI(), vCardUri, vCardFn, vCardHasEmail,
					vCardHasURL, vCardHasTelephone, "", nodeID));

		// Publisher
		if (publisherUri != null || publisherName != null || publisherMbox != null || publisherHomepage != null
				|| publisherType != null || publisherIdentifier != null)
			publisher = new FOAFAgent(DCTerms.publisher.getURI(), publisherUri, publisherName, publisherMbox,
					publisherHomepage, publisherType, publisherIdentifier, nodeID);
		// Rights Holder
		if (holderUri != null || holderName != null || holderMbox != null || holderHomepage != null
				|| holderType != null || holderIdentifier != null)
			rightsHolder = new FOAFAgent(DCTerms.rightsHolder.getURI(), holderUri, holderName, holderMbox,
					holderHomepage, holderType, holderIdentifier, nodeID);
		// Creator
		if (creatorUri != null || creatorName != null || creatorMbox != null || creatorHomepage != null
				|| creatorType != null || creatorIdentifier != null)
			creator = new FOAFAgent(DCTerms.creator.getURI(), creatorUri, creatorName, creatorMbox, creatorHomepage,
					creatorType, creatorIdentifier, nodeID);

		if (StringUtils.isBlank(releaseDate))
			releaseDate = "";
		if (StringUtils.isBlank(updateDate))
			updateDate = releaseDate;
		if (StringUtils.isBlank(landingPage))
			landingPage = doc.baseUri();

		identifier = landingPage;

		mapped = new DCATDataset(nodeID, identifier, title, description, distributionList, themeList, publisher,
				contactPointList, keywords, accessRights, conformsTo, documentation, frequency, hasVersion, isVersionOf,
				landingPage, language, provenance, releaseDate, updateDate, otherIdentifier, sample, source,
				spatialCoverage, temporalCoverage, type, version, versionNotes, rightsHolder, creator, subjectList,
				relatedResource);

		distributionList = null;
		publisher = null;
		contactPointList = null;

		return mapped;
	}

	/**
	 * Extract one or more values from the HTML document, using the input Selector
	 * and depending on its type (text, Link, ecc)
	 * 
	 * @param DatasetSelector sel - The Selector to be used to extract values from
	 *                        the document
	 * @return
	 */

	/*
	 * Extract the distribution from dataset page either through a list of
	 * Underscored selectors (e.g. distribution_0_title) or directly from the list
	 * of extracted multiple elements
	 */

	private List<DCATDistribution> manageDistributionSelectors(List<DatasetSelector> selectors, Elements elementList,
			Document document) {

		Map<String, DCATDistribution> staticMap = new HashMap<String, DCATDistribution>();
		Map<String, DCATDistribution> dinamicMap = new HashMap<String, DCATDistribution>();

		List<DatasetSelector> dinamicSelectors = new ArrayList<DatasetSelector>();

		/*
		 * **************STATIC DISTRIBUTION FETCH ***********************************
		 * Retrieve distributions by using statically defined selectors (e.g
		 * distribution_0_title)
		 */
		for (DatasetSelector sel : selectors) {
			String selName = sel.getName().trim();
			Matcher staticMatcher = staticPattern.matcher(selName);
			Matcher dinamicMatcher = dinamicPattern.matcher(selName);

			if (staticMatcher.find()) {

				// Extract index from selector name and check if the distribution is already in
				// the Map

				String index = null;

				// Get selector name and call the relative Distribution setter method, in order
				// to either complete or create a new Distribution
				if ((index = staticMatcher.group(2)) != null) {
					DCATDistribution distr = staticMap.getOrDefault(index, new DCATDistribution(nodeID));

					try {
						Method method = distr.getClass().getMethod("set" + WordUtils.capitalize(staticMatcher.group(3)),
								String.class);

						method.invoke(distr, fetchValueBySelector(document, sel));
						staticMap.put(index, distr);

					} catch (Exception e) {
						logger.info("Error while retrieving Distribution setter method from Selector Name:" + selName
								+ " - " + e.getMessage());
					}
				}

			} else if (dinamicMatcher.find()) {

				/*
				 * ************* DINAMIC DISTRIBUTIONS FETCH ****************************
				 * Retrieve distributions by using selectors for a page with a list of multiple
				 * similar items (e.g. distribution_title )
				 */
				// TODO Se il selettore è diverso da downloadURL, se matcha più di un elemento,
				// controllare che siano in numero uguali a quelli matchati da downloadURL.
				// Se matcha un elemento, si considera che i campi diversi da downloadURL siano
				// unici per tutte le distribution, perchè sono relativi al dataset intero

				String field = null;

				if ((field = dinamicMatcher.group(2)) != null) {

					if (field.equals("downloadURL")) {

						for (String downloadURLValue : fetchMultipleValuesBySelector(document, sel)) {
							DCATDistribution dist = new DCATDistribution(nodeID);
							dist.setDownloadURL(downloadURLValue);
							dist.setAccessURL(downloadURLValue);
							dinamicMap.put(downloadURLValue, dist);
						}

					} else {
						// Collect other dinamic selectors to be managed out of the iteration and after
						// downloadURLs have been fetched
						dinamicSelectors.add(sel);
					}

				}
			}
		}

		// If dinamicMap has been populated and the other dinamic selectors have been
		// collected,
		// tries to complete with other selectors the distributions created for each
		// downloadURL

		if (!dinamicMap.isEmpty() && !dinamicSelectors.isEmpty()) {

			for (DatasetSelector sel : dinamicSelectors) {
				String selName = sel.getName().trim();
				Matcher dinamicMatcher = dinamicPattern.matcher(selName);

				// Fetch Values from multiple Elements from the dataset page, by using current
				// selector (if it is a dinamic Selector: e.g. distribution_title)
				if (dinamicMatcher.find()) {
					List<String> values = fetchMultipleValuesBySelector(document, sel);

					if (!values.isEmpty()) {

						// Apply the single fetched value to each distribution
						if (values.size() == 1) {

							for (Map.Entry<String, DCATDistribution> entry : dinamicMap.entrySet()) {
								try {
									DCATDistribution distr = entry.getValue();
									Method method = distr.getClass().getMethod(
											"set" + WordUtils.capitalize(dinamicMatcher.group(2)), String.class);

									method.invoke(distr, values.get(0));
									dinamicMap.put(entry.getKey(), distr);
								} catch (Exception e) {
									logger.info("Error while retrieving Distribution setter method from Selector Name:"
											+ selName + " - " + e.getMessage());
								}
							}

						} else if (values.size() == dinamicMap.size()) {
							ListIterator<String> valuesIt = values.listIterator();
							Iterator<Entry<String, DCATDistribution>> entriesIt = dinamicMap.entrySet().iterator();

							while (valuesIt.hasNext()) {
								try {
									Entry<String, DCATDistribution> entry = entriesIt.next();
									DCATDistribution distr = entry.getValue();
									Method method = distr.getClass().getMethod(
											"set" + WordUtils.capitalize(dinamicMatcher.group(2)), String.class);

									method.invoke(distr, valuesIt.next());
									dinamicMap.put(entry.getKey(), distr);
								} catch (Exception e) {
									logger.info("Error while retrieving Distribution setter method from Selector Name:"
											+ selName + " - " + e.getMessage());
								}
							}
						}
					}
				}
			}

		}

		// Return the Distribution list coming from relative Map, also try to extract
		// format from file extension, if format field is empty
		if (dinamicMap.isEmpty())

			return staticMap.values().stream().map(distr ->

			{
				return StringUtils.isNotBlank(distr.getFormat().getValue()) ? distr
						: distr.setFormat(CommonUtil.extractFormatFromFileExtension(distr.getDownloadURL().getValue()));
			}).collect(Collectors.toList());
		else
			return dinamicMap.values().stream().map(distr ->

			{
				return StringUtils.isNotBlank(distr.getFormat().getValue()) ? distr
						: distr.setFormat(CommonUtil.extractFormatFromFileExtension(distr.getDownloadURL().getValue()));
			}).collect(Collectors.toList());

	}

	@Override
	public DCATDataset getDataset(String datasetId) throws Exception {

		return null;
	}

	@Override
	public List<DCATDataset> getAllDatasets() throws Exception {

		/*
		 * Call the WebScraper to get the Dataset Documents to be mapped
		 */
		List<Document> docs = WebScraper.getDatasetsDocument(node.getSitemap());

		AtomicInteger counter = new AtomicInteger(0);
		List<DCATDataset> totalDatasets = docs.stream().map(doc -> {
			try {
				return datasetToDCAT(doc, node);
			} catch (DatasetNotValidException e) {
				logger.info(e.getMessage());
				counter.getAndIncrement();
				return null;
			}
			// }).filter(item -> item != null).filter(distinctByKey(p ->
			// p.getTitle())).collect(Collectors.toList());
		}).filter(item -> item != null).collect(Collectors.toList());

		logger.info("Skipped Web datasets when mapping: " + counter.get() + "/" + docs.size());
		logger.info("Final mapped and returned datasets: " + totalDatasets.size() + "/" + docs.size());

		return totalDatasets;

	}

	@Override
	public ODMSSynchronizationResult getChangedDatasets(List<DCATDataset> oldDatasets, String startingDate)
			throws Exception {

		ArrayList<DCATDataset> newDatasets = (ArrayList<DCATDataset>) getAllDatasets();
		ODMSSynchronizationResult syncrhoResult = new ODMSSynchronizationResult();

		ImmutableSet<DCATDataset> newSets = ImmutableSet.copyOf(newDatasets);
		ImmutableSet<DCATDataset> oldSets = ImmutableSet.copyOf(oldDatasets);

		int deleted = 0, added = 0, changed = 0;

		/// Find added datasets
		// difference(current,present)
		SetView<DCATDataset> diff = Sets.difference(newSets, oldSets);
		logger.info("New Packages: " + diff.size());
		for (DCATDataset d : diff) {
			syncrhoResult.addToAddedList(d);
			added++;
		}

		// Find removed datasets
		// difference(present,current)
		SetView<DCATDataset> diff1 = Sets.difference(oldSets, newSets);
		logger.info("Deleted Packages: " + diff1.size());
		for (DCATDataset d : diff1) {
			syncrhoResult.addToDeletedList(d);
			deleted++;
		}

		// Find updated datasets
		// intersection(present,current)
		SetView<DCATDataset> intersection = Sets.intersection(newSets, oldSets);
		logger.fatal("Changed Packages: " + intersection.size());

		for (DCATDataset d : intersection) {
			syncrhoResult.addToChangedList(d);
			changed++;
		}

		logger.info("Changed " + syncrhoResult.getChangedDatasets().size());
		logger.info("Added " + syncrhoResult.getAddedDatasets().size());
		logger.info("Deleted " + syncrhoResult.getDeletedDatasets().size());
		logger.info("Expected new dataset count: " + (node.getDatasetCount() - deleted + added));

		return syncrhoResult;
	}

	private static <T> Predicate<T> distinctByKey(Function<? super T, Object> keyExtractor) {
		Map<Object, Boolean> map = new ConcurrentHashMap<>();
		return t -> map.putIfAbsent(keyExtractor.apply(t), Boolean.TRUE) == null;
	}

	private static List<String> fetchMultipleValuesBySelector(Document document, DatasetSelector sel) {
		Elements extractedElements = document.select(sel.getSelector().replaceAll("'", ""));
		List<String> extractedValues = null;

		switch (sel.getType()) {

		case SelectorElementAttribute:
			extractedValues = extractedElements.stream().map(e -> {

				String extractedAttr = e.attr(sel.getExtractAttribute());
				/*
				 * If the selector is downloadURL, extract the url
				 */
				if (sel.getName().contains("downloadURL"))
					extractedAttr = extractDownloadUrl(extractedAttr);

				return extractedAttr;
			}).collect(Collectors.toList());

			break;
		case SelectorText:
			extractedValues = extractedElements.stream().map(e -> {

				/*
				 * ** Extract the text from the HTML element with Regex if any in the selector
				 */
				String extractedText = e.text();
				String regex = sel.getRegex();
				if (StringUtils.isNotBlank(regex)) {
					Matcher regexMatcher = Pattern.compile(regex).matcher(extractedText);
					if (regexMatcher.find()) {
						extractedText = regexMatcher.group();
					}
				}

				/*
				 * If the selector is downloadURL, extract the url
				 */
				if (sel.getName().contains("downloadURL"))
					extractedText = extractDownloadUrl(extractedText);

				return extractedText;
			}).collect(Collectors.toList());

			break;
		case SelectorLink:
			// TODO Manage specific link selector fields
			break;

		default:
			break;

		}

		return extractedValues;
	}

	private static String extractDownloadUrl(String extractedText) {
		Matcher downloadMatcher = downloadUrlPattern.matcher(extractedText);
		if (downloadMatcher.find()) {
			String argument = null;
			if ((argument = downloadMatcher.group(1)) != null)
				extractedText = argument.replaceAll("'", "");
		}
		return extractedText;
	}

	private static String fetchValueBySelector(Document document, DatasetSelector sel) {

		String fetchValue = null;
		if (sel.getType().equals(WebScraperSelectorType.SelectorElementAttribute))
			fetchValue = document.select(sel.getSelector()).attr(sel.getExtractAttribute());
		else
			fetchValue = document.select(sel.getSelector()).text();

		if (sel.getName().contains("downloadURL")) {

			Matcher downloadMatcher = Pattern.compile("\\w*\\(([^)]+)\\);*").matcher(fetchValue);
			if (downloadMatcher.find()) {
				String argument = null;
				if ((argument = downloadMatcher.group(1)) != null) {
					fetchValue = argument.replaceAll("'", "");
				}
			}
		}
		return fetchValue;
	}

	/*
	 * Return a List of SKOSConcept, each of them containing a prefLabel from input
	 * String list.
	 */

	private <T extends SKOSConcept> List<T> extractConceptList(String propertyUri, List<String> concepts,
			Class<T> type) {
		List<T> result = new ArrayList<T>();

		for (String label : concepts) {
			try {
				result.add(type.getDeclaredConstructor(SKOSConcept.class).newInstance(
						new SKOSConcept(propertyUri, "", Arrays.asList(new SKOSPrefLabel("", label, nodeID)), nodeID)));
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException e) {
				e.printStackTrace();
			}
		}
		return result;
	}

}
