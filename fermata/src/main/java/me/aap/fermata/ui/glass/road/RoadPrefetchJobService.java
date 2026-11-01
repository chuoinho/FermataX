package me.aap.fermata.ui.glass.road;

import android.app.job.JobParameters;
import android.app.job.JobService;

import java.util.List;

public class RoadPrefetchJobService extends JobService {

    private volatile boolean stopped = false;
    private Thread workerThread = null;

    @Override
    public boolean onStartJob(JobParameters params) {
        if (!RoadPrefetch.isEnabled(this)) {
            return false;
        }

        stopped = false;
        workerThread = new Thread(() -> {
            boolean reschedule = false;
            try {
                RoadTileStore store = RoadTileStore.get(this);
                RoadDataSource source = RoadDataSource.get(this);
                List<TileId> work;

                if (params.getJobId() == RoadPrefetch.JOB_CATCH_UP) {
                    work = RoadPrefetch.catchUpWork(store);
                } else if (params.getJobId() == RoadPrefetch.JOB_REFRESH) {
                    work = RoadPrefetch.refreshWork(store, System.currentTimeMillis());
                } else {
                    work = null;
                }

                if (work != null) {
                    int size = work.size();
                    for (int i = 0; i < size; i++) {
                        if (stopped || Thread.currentThread().isInterrupted()) {
                            reschedule = true;
                            break;
                        }
                        TileId t = work.get(i);
                        source.download(t);

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
                jobFinished(params, reschedule);
            }
        }, "RoadPrefetchJob-" + params.getJobId());

        workerThread.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        stopped = true;
        if (workerThread != null) {
            workerThread.interrupt();
        }
        return true;
    }
}
