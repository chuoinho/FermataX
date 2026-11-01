package me.aap.fermata.ui.glass.road;

import android.app.job.JobParameters;
import android.app.job.JobService;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RoadPrefetchJobService extends JobService {

    private final Map<Integer, Thread> workers = new ConcurrentHashMap<>();
    private final Set<Integer> stoppedJobs = ConcurrentHashMap.newKeySet();

    @Override
    public boolean onStartJob(JobParameters params) {
        if (!RoadPrefetch.isEnabled(this)) {
            return false;
        }

        int jobId = params.getJobId();
        stoppedJobs.remove(jobId);
        Thread workerThread = new Thread(() -> {
            boolean reschedule = false;
            try {
                RoadTileStore store = RoadTileStore.get(this);
                RoadDataSource source = RoadDataSource.get(this);
                List<TileId> work;

                if (jobId == RoadPrefetch.JOB_CATCH_UP) {
                    work = RoadPrefetch.catchUpWork(store);
                } else if (jobId == RoadPrefetch.JOB_REFRESH) {
                    work = RoadPrefetch.refreshWork(store, System.currentTimeMillis());
                } else {
                    work = null;
                }

                if (work != null) {
                    int size = work.size();
                    for (int i = 0; i < size; i++) {
                        if (stoppedJobs.contains(jobId) || Thread.currentThread().isInterrupted()) {
                            reschedule = true;
                            break;
                        }
                        TileId t = work.get(i);
                        boolean ok = source.download(t);
                        if (!ok) {
                            reschedule = true;
                            break;
                        }

                        if (i < size - 1) {
                            try {
                                Thread.sleep(1500L);
                            } catch (InterruptedException e) {
                                reschedule = true;
                                break;
                            }
                        }
                    }
                }
            } finally {
                workers.remove(jobId);
                stoppedJobs.remove(jobId);
                jobFinished(params, reschedule);
            }
        }, "RoadPrefetchJob-" + jobId);

        workers.put(jobId, workerThread);
        workerThread.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        int jobId = params.getJobId();
        stoppedJobs.add(jobId);
        Thread t = workers.remove(jobId);
        if (t != null) {
            t.interrupt();
        }
        return true;
    }
}
