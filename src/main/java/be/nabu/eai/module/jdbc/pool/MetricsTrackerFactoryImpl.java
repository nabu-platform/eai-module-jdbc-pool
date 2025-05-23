/*
* Copyright (C) 2015 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.eai.module.jdbc.pool;

import java.util.concurrent.TimeUnit;

import be.nabu.libs.metrics.api.MetricGauge;
import be.nabu.libs.metrics.api.MetricInstance;

import com.zaxxer.hikari.metrics.MetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.PoolStats;

public class MetricsTrackerFactoryImpl implements MetricsTrackerFactory {

	public static final String METRIC_WAIT = "waitForConnection";
	public static final String METRIC_USE = "useConnection";
	public static final String METRIC_ACTIVE = "activeConnections";
	public static final String METRIC_IDLE = "idleConnections";
	public static final String METRIC_TOTAL = "totalConnections";
	public static final String METRIC_PENDING = "pendingConnections";
	
	private MetricInstance metrics;

	public MetricsTrackerFactoryImpl(MetricInstance metrics) {
		this.metrics = metrics;
	}
	
	@Override
	public MetricsTracker create(String poolName, final PoolStats poolStats) {
		metrics.set(METRIC_TOTAL, new MetricGauge() {
			@Override
			public long getValue() {
				return poolStats.getTotalConnections();
			}
		});
		metrics.set(METRIC_IDLE, new MetricGauge() {
			@Override
			public long getValue() {
				return poolStats.getIdleConnections();
			}
		});
		metrics.set(METRIC_ACTIVE, new MetricGauge() {
			@Override
			public long getValue() {
				return poolStats.getActiveConnections();
			}
		});
		metrics.set(METRIC_PENDING, new MetricGauge() {
			@Override
			public long getValue() {
				return poolStats.getPendingThreads();
			}
		});
		// based on the tracker here: https://github.com/brettwooldridge/HikariCP/blob/5553fe5dcf0cc6f53b97d82ac0ef97a7cd010b12/src/main/java/com/zaxxer/hikari/metrics/dropwizard/CodaHaleMetricsTracker.java
		// the logic assumptions are based on: https://github.com/brettwooldridge/HikariCP/blob/a491250ba1f547d289b04b9cd0c9f4bbda6972c7/src/main/java/com/zaxxer/hikari/pool/HikariPool.java
		return new MetricsTracker() {
			@Override
			public void recordConnectionAcquiredNanos(long elapsedAcquiredNanos) {
				metrics.duration(METRIC_WAIT, elapsedAcquiredNanos, TimeUnit.NANOSECONDS);
			}
			@Override
			public void recordConnectionUsageMillis(long elapsedBorrowedMillis) {
				metrics.duration(METRIC_USE, elapsedBorrowedMillis, TimeUnit.MILLISECONDS);
			}
		};
	}

}
