package calibration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.xml.sax.SAXException;

import createPTGTFS.FareCalculatorPTGTFS;
import dynamicTransitRouter.fareCalculators.FareCalculator;
import dynamicTransitRouter.fareCalculators.LRFareCalculator;
import dynamicTransitRouter.fareCalculators.MTRFareCalculator;
import dynamicTransitRouter.fareCalculators.UniformFareCalculator;
import dynamicTransitRouter.fareCalculators.ZonalFareXMLParserV2;
import optimizerAgent.MaaSUtil;
import optimizerAgent.SimpleTranslatedPlan;
import transitCalculatorsWithFare.FareLink;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelRoute;
import ust.hk.praisehk.metamodelcalibration.analyticalModel.AnalyticalModelTransitRoute;

public class MetamodelPopulationGeneration {
	public static final String probKey  = "metaModelPlanProb";
	public static final String ifMaaSActivatedKey = "ifMaaSActivated";
	public static final String fullFareKey = "fullFare";
	public static final String MTRFareKey = "mtrFare";
	public static final String busFareKey = "busFare";
	public static final String ferryFareKey = "ferryFare";
	public static final String EMutilityKey = "EMutil";
	public static Population readAndPreparePopulation(String populationLoc, Map<String,Tuple<Double,Double>> timeBean, Scenario scenario, Map<String,FareCalculator>fareCalculators) {
		Population pop = PopulationUtils.readPopulation(populationLoc);
		Set<String> subPops = new HashSet<>();
		int popNo = 0;
		pop.getPersons().entrySet().forEach(p->{
			boolean noMaas = true;
			//if(!PopulationUtils.getSubpopulation(p.getValue()).contains("GV")) {
				subPops.add(PopulationUtils.getSubpopulation(p.getValue()));
				List<Double>utils = new ArrayList<>();
				for(Plan pl:p.getValue().getPlans()) {
					SimpleTranslatedPlan trPlan = new SimpleTranslatedPlan(timeBean, pl, scenario);
					pl.getAttributes().putAttribute(SimpleTranslatedPlan.SimplePlanAttributeName, trPlan);
					if(pl.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName)!=null) noMaas = false;
					double fare = 0;
					double mtrFare = 0;
					double busFare = 0;
					double ferryFare = 0;
					for(Entry<String, List<FareLink>> tfareLink:trPlan.getFareLinkUsage().entrySet()){
						for(FareLink fl:tfareLink.getValue()) {
							double fullFare = fareCalculators.get(fl.getMode()).getFares(fl.getTransitRoute(), fl.getTransitLine(), fl.getBoardingStopFacility(), fl.getAlightingStopFacility()).get(0);
							if(fl.getMode().equals("train"))mtrFare+=fullFare;
							else if(fl.getMode().equals("bus"))busFare+=fullFare;
							else if(fl.getMode().equals("ferry"))ferryFare+=fullFare;
							fare+=fullFare;
						}
					}
					pl.getAttributes().putAttribute(fullFareKey,fare);
					pl.getAttributes().putAttribute(MTRFareKey,mtrFare);
					pl.getAttributes().putAttribute(busFareKey,busFare);
					pl.getAttributes().putAttribute(ferryFareKey,ferryFare);
					String eleUtilString = (String) pl.getAttributes().getAttribute("ElelmentUtil");
					Map<String,Double> eleUtils = convertStringToMap(eleUtilString);
					double util = 0;
					for(double d:eleUtils.values())util+=d;
					pl.getAttributes().putAttribute("util",util);
					utils.add(util);
					
				}
				double emu = 0;
				double max = Collections.max(utils);
				for(double d:utils) {
					emu+=Math.exp(d-max);
				}
				emu = Math.log(emu);
				emu+=max;
				p.getValue().getAttributes().putAttribute(EMutilityKey,emu);
				if(!noMaas)p.getValue().getAttributes().putAttribute(ifMaaSActivatedKey,!noMaas);
			//}
		
		});
		System.out.println(subPops);
		System.out.println(pop.getPersons().size());
		return pop;
	}
	public static Map<String,Double> convertStringToMap(String str){
		str = str.replace("{", "");
		str = str.replace("}", "");
		Map<String,Double> map = new HashMap<>();
		String[] prt = str.split(",");
		for(String item:prt) {
			map.put(item.split("=")[0],Double.parseDouble(item.split("=")[1]));
		}
		return map;
	}
	public static void main(String[] args) {
		String writeLoc ="test\\Results\\AllPack\\newResult\\govtResult25\\GovtGovtTUJul11metaModelPopFUllDisc99.xml";
		String popWriteLoc = writeLoc.replace(".csv", "PersonDetails.csv");
		Config config = singlePlanAlgo.RunUtils.provideConfig();
		Scenario scenario = ScenarioUtils.loadScenario(config);
		double startTime = config.qsim().getStartTime().seconds();
		double endTime = config.qsim().getEndTime().seconds();
		Map<String,Tuple<Double,Double>> timeBeans = new HashMap<>();
		int hour = ((int)startTime/3600)+1;
		for(double ii = 0; ii < endTime; ii = ii+3600) {
			timeBeans.put(Integer.toString(hour), new Tuple<>(ii,ii+3600));
			hour = hour + 1;
		}
		
		Map<String,FareCalculator>fareCalculators = new HashMap<>();
		SAXParser saxParser;
		ZonalFareXMLParserV2 busFareGetter = new ZonalFareXMLParserV2(scenario.getTransitSchedule());
		try {
			saxParser = SAXParserFactory.newInstance().newSAXParser();
			saxParser.parse("new Data/data/busFare.xml", busFareGetter);
			
		} catch (ParserConfigurationException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (SAXException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		try {
			fareCalculators.put("train", new MTRFareCalculator("fare/mtr_lines_fares.csv",scenario.getTransitSchedule()));
			fareCalculators.put("bus", FareCalculatorPTGTFS.loadFareCalculatorPTGTFS("fare/busFareGTFS.json"));
			fareCalculators.put("minibus", busFareGetter.get());
			fareCalculators.put("LR", new LRFareCalculator("fare/light_rail_fares.csv"));
			fareCalculators.put("ferry",FareCalculatorPTGTFS.loadFareCalculatorPTGTFS("fare/ferryFareGTFS.json"));
			fareCalculators.put("tram", new UniformFareCalculator(2.6));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Population pop = readAndPreparePopulation("test\\Results\\AllPack\\newResult\\govtResult25\\GovtGovtTUJul11metaModelPopFUllDisc99.xml",timeBeans,scenario,fareCalculators);
		
		try {
			FileWriter fw = new FileWriter(new File(writeLoc));
			FileWriter fwPop = new FileWriter(new File(popWriteLoc));
			fw.append("PId,PlanId,subPop,prob,fare,ifPersonHasMaas,ifPlanHasMaas,ActNo,AutoTripNo,TransitTripNo,fareLinkLegs,busLeg,trainLeg,ferryLeg,busFare,MTRFare,FerryFare,EMutility,Act1Coord\n");
			
			fwPop.append("PId,subPop,ifPersonHasMaaS,MaasChoiceProb,avgFare,avgActNo,avgAutoTrip,avgTrTrip,avgFareLinkLegs,avgBusLeg,avgTrainLeg,avgferryLeg,avgMTRFare,avgBusFare,avgFerryFare,avgEMUtility\n");
			
			for(Person p:pop.getPersons().values()) {
				int i = 0;
				double avgActsNo = 0;
				double avgAutoTripNo = 0;
				double avgTransitTripNo = 0;
				double avgtransitFareLeg = 0;
				double avgBusTrip = 0;
				double avgTrainTrip = 0;
				double avgferryTrip = 0;
				double avgbusFare = 0;
				double avgMTRFare = 0;
				double avgFerryFare = 0;
				double avgTotalFare = 0;
				double maasChoiceProb = 0;
				boolean ifHasMaas = p.getAttributes().getAttribute(ifMaaSActivatedKey)!=null;
				String subPop = PopulationUtils.getSubpopulation(p);
				double EMUtils = (double) p.getAttributes().getAttribute(EMutilityKey);
				for(Plan pl:p.getPlans()) {
					//if(pl.getAttributes().getAttribute(fullFareKey)!=null && (double)pl.getAttributes().getAttribute(fullFareKey)!=0) {
						SimpleTranslatedPlan trPlan = (SimpleTranslatedPlan) pl.getAttributes().getAttribute(SimpleTranslatedPlan.SimplePlanAttributeName);
						double actsNo = 0;
						double autoTripNo = 0;
						double transitTripNo = 0;
						double transitFareLeg = 0;
						double busTrip = 0;
						double trainTrip = 0;
						double ferryTrip = 0;
						double busFare = (double) pl.getAttributes().getAttribute(busFareKey);
						double mtrFare = (double) pl.getAttributes().getAttribute(MTRFareKey);
						double ferryFare = (double) pl.getAttributes().getAttribute(ferryFareKey);
						double planProb = (double) pl.getAttributes().getAttribute(probKey);
						
						if(trPlan!=null) {
						actsNo = trPlan.getActivities().size();
						for(Map<Id<AnalyticalModelRoute>, AnalyticalModelRoute> d:trPlan.getRoutes().values()){
							autoTripNo+=d.size();
						}
						for(Map<Id<AnalyticalModelTransitRoute>, AnalyticalModelTransitRoute> d:trPlan.getTrroutes().values()){
							transitTripNo+=d.size();
						}
						for(List<FareLink> d:trPlan.getFareLinkUsage().values()){
							transitFareLeg+=d.size();
							for(FareLink fl: d) {
								if(fl.getMode().equals("bus")) {
									busTrip++;
								}else if(fl.getMode().equals("train")) {
									trainTrip++;
									
								}else if(fl.getMode().equals("ferry")) {
									ferryTrip++;
								}
							}
						}
						}
						
						avgActsNo += planProb*actsNo;
						avgAutoTripNo += planProb*autoTripNo;
						avgTransitTripNo += planProb*transitTripNo;
						avgtransitFareLeg += planProb*transitFareLeg;
						avgBusTrip += planProb*busTrip;
						avgTrainTrip += planProb*trainTrip;
						avgferryTrip += planProb*ferryTrip;
						avgbusFare += planProb*busFare;
						avgMTRFare += planProb*mtrFare;
						avgFerryFare += planProb*ferryFare;
						avgTotalFare += planProb*(double)pl.getAttributes().getAttribute(fullFareKey);
						if(pl.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName)!=null)maasChoiceProb += planProb;
						
						fw.append(p.getId()+","+ p.getId()+"_"+i+","+PopulationUtils.getSubpopulation(p)+","+pl.getAttributes().getAttribute(probKey)+","+
								pl.getAttributes().getAttribute(fullFareKey)+","+p.getAttributes().getAttribute(ifMaaSActivatedKey)+","+
								(pl.getAttributes().getAttribute(MaaSUtil.CurrentSelectedMaaSPackageAttributeName)!=null)
								+","+actsNo+","+autoTripNo+","+transitTripNo+","+transitFareLeg+","+busTrip+","+trainTrip+","+ferryTrip
								+","+busFare+","+mtrFare+","+ferryFare+","+pl.getAttributes().getAttribute("util")+","+((Activity)pl.getPlanElements().get(0)).getCoord().getX()+"_"+((Activity)pl.getPlanElements().get(0)).getCoord().getY()+"\n");
						fw.flush();
					//}
				}
				//fwPop.append("PId,subPop,ifPersonHasMaaS,MaasChoiceProb,avgFare,avgActNo,avgAutoTrip,avgTrTrip,avgFareLinkLegs,avgBusLeg,avgTrainLeg,avgferryLeg,avgMTRFare,avgBusFare,avgFerryFare,avgEMUtility\n");
				fwPop.append(p.getId()+","+subPop+","+ifHasMaas+","+maasChoiceProb+","+avgTotalFare+","+avgActsNo+","+avgAutoTripNo
						+","+avgTransitTripNo+","+avgtransitFareLeg+","+avgBusTrip+","+avgTrainTrip+","+avgferryTrip+","+avgMTRFare+","+avgbusFare+","+avgFerryFare
						+","+EMUtils+"\n");
				fwPop.flush();
				
			}
			fw.close();
			fwPop.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
