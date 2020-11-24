package singlePlanAlgo;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.inject.Inject;

import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.utils.charts.XYLineChart;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.io.UncheckedIOException;

public class VehicleDistanceTravelledControlerListener
		implements StartupListener, IterationEndsListener, IterationStartsListener {
	
	private final Network network;
	private VehicleDistanceTravelled vdt;
	private Map<String, Map<Integer, Double>> travelParaHistories;
	
	
	private int firstIteration;
	
	@Inject private OutputDirectoryHierarchy controlerIO;
	
	private String fileLoc;

	@Inject
	VehicleDistanceTravelledControlerListener(Network net) {
		this.network = net;
		
	}
	
	public VehicleDistanceTravelledControlerListener(Network net, String fileloc) {
		this.network = net;
		this.fileLoc = fileloc;
		
	}
	
	@Override
	public void notifyIterationStarts(IterationStartsEvent event) {
		//Put the event handler inside.
		vdt = new VehicleDistanceTravelled(this.network);
		event.getServices().getEvents().addHandler(vdt);
	}

	public void notifyIterationStarts(EventsManager event) {
		//Put the event handler inside.
		vdt = new VehicleDistanceTravelled(this.network);
		event.addHandler(vdt);
	}
	
	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		travelParaHistories.get("ptDistance").put(event.getIteration(), vdt.getPTTravelDist());
		travelParaHistories.get("otherDistance").put(event.getIteration(), vdt.getTravelDist());
		travelParaHistories.get("ptTime").put(event.getIteration(), vdt.getPTTravelTime());
		travelParaHistories.get("otherTime").put(event.getIteration(), vdt.getTravelTime());
		
		BufferedWriter modeOut = IOUtils.getBufferedWriter(controlerIO.getOutputFilename("systemTravelInfo.txt"));
		try {
			modeOut.write("Iteration\t"+"ptDistance\t"+"otherDistance\t"+"ptTime\t"+"otherTime\n");
			for (int iter = firstIteration; iter <= event.getIteration(); iter++) {
				modeOut.write( String.valueOf(iter) ) ;
				for ( Map<Integer, Double> dataValue : travelParaHistories.values() ) {
					modeOut.write( "\t" + dataValue.get(iter)) ;
				}
				modeOut.write( "\n" ) ;
			}
			modeOut.flush();
			modeOut.close();
		} catch (IOException e) {
			e.printStackTrace();
			throw new UncheckedIOException(e);
		}

		// Plot the distance graph
		XYLineChart chart_dist = new XYLineChart("Mode Statistics", "iteration", "distance_km");
		for ( Entry<String, Map<Integer, Double>> entry : this.travelParaHistories.entrySet() ) {
			String mode = entry.getKey() ;
			if(mode.contains("Distance")) {
				Map<Integer, Double> history = entry.getValue() ;
				chart_dist.addSeries(mode, history ) ;
			}
		}
		chart_dist.saveAsPng(controlerIO.getOutputFilename("travelDistance.png"), 800, 600);
		
		//Plot the time graph
		XYLineChart chart_time = new XYLineChart("Mode Statistics", "iteration", "travel_time_s");
		for ( Entry<String, Map<Integer, Double>> entry : this.travelParaHistories.entrySet() ) {
			String mode = entry.getKey() ;
			if(mode.contains("Time")) {
				Map<Integer, Double> history = entry.getValue() ;
				chart_time.addSeries(mode, history ) ;
			}
		}
		chart_time.saveAsPng(controlerIO.getOutputFilename("travelTime.png"), 800, 600);
	}
	
	public void notifyIterationEnds() {
		travelParaHistories.get("ptDistance").put(1, vdt.getPTTravelDist());
		travelParaHistories.get("otherDistance").put(1, vdt.getTravelDist());
		travelParaHistories.get("ptTime").put(1, vdt.getPTTravelTime());
		travelParaHistories.get("otherTime").put(1, vdt.getTravelTime());
		
		BufferedWriter modeOut = IOUtils.getBufferedWriter(fileLoc + "systemTravelInfo.txt");
		try {
			modeOut.write("Iteration\t"+"ptDistance\t"+"otherDistance\t"+"ptTime\t"+"otherTime\n");
			for (int iter = firstIteration; iter <= 1; iter++) {
				modeOut.write( String.valueOf(iter) ) ;
				for ( Map<Integer, Double> dataValue : travelParaHistories.values() ) {
					modeOut.write( "\t" + dataValue.get(iter)) ;
				}
				modeOut.write( "\n" ) ;
			}
			modeOut.flush();
			modeOut.close();
		} catch (IOException e) {
			e.printStackTrace();
			throw new UncheckedIOException(e);
		}

		// Plot the distance graph
		XYLineChart chart_dist = new XYLineChart("Mode Statistics", "iteration", "distance_km");
		for ( Entry<String, Map<Integer, Double>> entry : this.travelParaHistories.entrySet() ) {
			String mode = entry.getKey() ;
			if(mode.contains("Distance")) {
				Map<Integer, Double> history = entry.getValue() ;
				chart_dist.addSeries(mode, history ) ;
			}
		}
		chart_dist.saveAsPng(fileLoc+"travelDistance.png", 800, 600);
		
		//Plot the time graph
		XYLineChart chart_time = new XYLineChart("Mode Statistics", "iteration", "travel_time_s");
		for ( Entry<String, Map<Integer, Double>> entry : this.travelParaHistories.entrySet() ) {
			String mode = entry.getKey() ;
			if(mode.contains("Time")) {
				Map<Integer, Double> history = entry.getValue() ;
				chart_time.addSeries(mode, history ) ;
			}
		}
		chart_time.saveAsPng(fileLoc+"travelTime.png", 800, 600);
	}
	
	@Override
	public void notifyStartup(StartupEvent event) {
		firstIteration = event.getServices().getIterationNumber();
		travelParaHistories = new HashMap<>();
		travelParaHistories.put("ptDistance", new HashMap<>());
		travelParaHistories.put("otherDistance", new HashMap<>());
		travelParaHistories.put("ptTime", new HashMap<>());
		travelParaHistories.put("otherTime", new HashMap<>());
	}
	
	
	public void initialize() {
		firstIteration = 1;
		travelParaHistories = new HashMap<>();
		travelParaHistories.put("ptDistance", new HashMap<>());
		travelParaHistories.put("otherDistance", new HashMap<>());
		travelParaHistories.put("ptTime", new HashMap<>());
		travelParaHistories.put("otherTime", new HashMap<>());
	}
}



