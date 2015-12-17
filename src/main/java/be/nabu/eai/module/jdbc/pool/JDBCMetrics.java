package be.nabu.eai.module.jdbc.pool;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;

public class JDBCMetrics {
	
	private double meanWait, oneMinuteWait, fiveMinuteWait, fifteenMinuteWait;
	private long minUse, maxUse, totalUses;
	private double meanUse, medianUse;
	private int totalConnections, idleConnections, activeConnections, pendingConnections;
	
	/**
	 * Based on the information here: https://github.com/brettwooldridge/HikariCP/wiki/Dropwizard-Metrics
	 * @return 
	 */
	public static JDBCMetrics build(String id, MetricRegistry registry) {
		JDBCMetrics instance = new JDBCMetrics();
		
		Timer timer = registry.getTimers().get(id + ".pool.Wait");
		instance.setMeanWait(timer.getMeanRate());
		instance.setOneMinuteWait(timer.getOneMinuteRate());
		instance.setFiveMinuteWait(timer.getFiveMinuteRate());
		instance.setFifteenMinuteWait(timer.getFifteenMinuteRate());
		
		Histogram histogram = registry.getHistograms().get(id + ".pool.Usage");
		Snapshot snapshot = histogram.getSnapshot();
		instance.setMinUse(snapshot.getMin());
		instance.setMaxUse(snapshot.getMax());
		instance.setMeanUse(snapshot.getMean());
		instance.setMedianUse(snapshot.getMedian());
		instance.setTotalUses(histogram.getCount());
		
		instance.setActiveConnections((Integer) registry.getGauges().get(id + ".pool.ActiveConnections").getValue());
		instance.setTotalConnections((Integer) registry.getGauges().get(id + ".pool.TotalConnections").getValue());
		instance.setIdleConnections((Integer) registry.getGauges().get(id + ".pool.IdleConnections").getValue());
		instance.setPendingConnections((Integer) registry.getGauges().get(id + ".pool.PendingConnections").getValue());
		
		return instance;
	}

	public double getMeanWait() {
		return meanWait;
	}
	public void setMeanWait(double meanWait) {
		this.meanWait = meanWait;
	}

	public double getOneMinuteWait() {
		return oneMinuteWait;
	}
	public void setOneMinuteWait(double oneMinuteWait) {
		this.oneMinuteWait = oneMinuteWait;
	}

	public double getFiveMinuteWait() {
		return fiveMinuteWait;
	}
	public void setFiveMinuteWait(double fiveMinuteWait) {
		this.fiveMinuteWait = fiveMinuteWait;
	}

	public double getFifteenMinuteWait() {
		return fifteenMinuteWait;
	}
	public void setFifteenMinuteWait(double fifteenMinuteWait) {
		this.fifteenMinuteWait = fifteenMinuteWait;
	}

	public long getMinUse() {
		return minUse;
	}

	public void setMinUse(long minUse) {
		this.minUse = minUse;
	}

	public long getMaxUse() {
		return maxUse;
	}

	public void setMaxUse(long maxUse) {
		this.maxUse = maxUse;
	}

	public double getMeanUse() {
		return meanUse;
	}

	public void setMeanUse(double meanUse) {
		this.meanUse = meanUse;
	}

	public double getMedianUse() {
		return medianUse;
	}

	public void setMedianUse(double medianUse) {
		this.medianUse = medianUse;
	}

	public long getTotalUses() {
		return totalUses;
	}

	public void setTotalUses(long totalUses) {
		this.totalUses = totalUses;
	}

	public int getTotalConnections() {
		return totalConnections;
	}

	public void setTotalConnections(int totalConnections) {
		this.totalConnections = totalConnections;
	}

	public int getIdleConnections() {
		return idleConnections;
	}

	public void setIdleConnections(int idleConnections) {
		this.idleConnections = idleConnections;
	}

	public int getActiveConnections() {
		return activeConnections;
	}

	public void setActiveConnections(int activeConnections) {
		this.activeConnections = activeConnections;
	}

	public int getPendingConnections() {
		return pendingConnections;
	}

	public void setPendingConnections(int pendingConnections) {
		this.pendingConnections = pendingConnections;
	}
}
