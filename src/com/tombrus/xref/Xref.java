package com.tombrus.xref;

import static com.tombrus.xref.U.*;
import static java.net.HttpURLConnection.*;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tombrus.persistentqueues.Actor;
import com.tombrus.persistentqueues.PQueue;
import com.tombrus.persistentqueues.PQueueActor;
import com.tombrus.persistentqueues.PQueueGroup;
import com.tombrus.xref.Repo.ErrorRepo;
import com.tombrus.xref.Repo.GotWfsRepo;
import com.tombrus.xref.Repo.NamedRepo;
import com.tombrus.xref.Repo.NoRepo;
import com.tombrus.xref.Repo.NoWfRepo;
import com.tombrus.xref.Repo.WfRepo;

@SuppressWarnings("FieldCanBeLocal")
public class Xref extends PQueueGroup {
    private static final int NUM_SAMPLERS                = 20;
    private static final int NUM_GETTERS                 = 3;
    private static final int MAX_TMPQUEUE_SIZE           = 1000;
    private static final int RATE_LIMIT_RESET_CORRECTION = 60 * 60;

    private final String                   token;
    //
    private final PQueue<NoRepo>           absentQueue;
    private final PQueue<NamedRepo>        tmpNamedQueue;
    private final PQueue<NoWfRepo>         nowfQueue;
    private final PQueue<WfRepo>           wfQueue;
    private final PQueue<GotWfsRepo>       gotWfsQueue;
    private final PQueue<ErrorRepo>        errorsQueue;
    //
    private final Explorer                 explorer;
    private final List<HasWorkflowSampler> hasWorkflowSamplers;
    private final List<WfsGetter>          wfsGetters;

    public Xref(Path dir, String token, int saveIntervalSec) {
        super(dir, saveIntervalSec);
        this.token = token;

        absentQueue = new PQueue<>(NoRepo.RECREATOR, "absent", getDir());
        tmpNamedQueue = new PQueue<>(NamedRepo.RECREATOR, "tmp-named", getDir());
        nowfQueue = new PQueue<>(NoWfRepo.RECREATOR, "nowf", getDir());
        wfQueue = new PQueue<>(WfRepo.RECREATOR, "wf", getDir());
        gotWfsQueue = new PQueue<>(GotWfsRepo.RECREATOR, "gotwfs", getDir());
        errorsQueue = new PQueue<>(ErrorRepo.RECREATOR, "errors", getDir());

        explorer = new Explorer();
        hasWorkflowSamplers = IntStream.range(0, NUM_SAMPLERS).mapToObj(HasWorkflowSampler::new).collect(Collectors.toList());
        wfsGetters = IntStream.range(0, NUM_GETTERS).mapToObj(WfsGetter::new).collect(Collectors.toList());

        add(absentQueue);
        add(tmpNamedQueue);
        add(nowfQueue);
        add(wfQueue);
        add(gotWfsQueue);
        add(errorsQueue);

        add(explorer);
        hasWorkflowSamplers.forEach(this::add);
        wfsGetters.forEach(this::add);

        restore();
    }

    @Override
    protected void actualSave() {
        super.actualSave();
        new GenXref(this, getDir().resolve("..").resolve("docs").resolve("index.html")).generate();
    }

    public PQueue<NoRepo> getAbsentQueue() {
        return absentQueue;
    }

    public PQueue<NamedRepo> getTmpNamedQueue() {
        return tmpNamedQueue;
    }

    public PQueue<WfRepo> getWfQueue() {
        return wfQueue;
    }

    public PQueue<NoWfRepo> getNowfQueue() {
        return nowfQueue;
    }

    public PQueue<GotWfsRepo> getGotWfsQueue() {
        return gotWfsQueue;
    }

    public PQueue<ErrorRepo> getErrorsQueue() {
        return errorsQueue;
    }

    public boolean isRateLimited() {
        return explorer.isRateLimited();
    }

    public boolean isActorAPaused() {
        return explorer.isPaused();
    }

    public boolean isActorBPaused() {
        return hasWorkflowSamplers.stream().allMatch(Actor::isPaused);
    }

    public boolean isActorCPaused() {
        return wfsGetters.stream().allMatch(Actor::isPaused);
    }

    private class Explorer extends Actor {
        private final Random random = new Random();
        private       int    numErrors;
        private       long   dozeUntilSec;

        public Explorer() {
            super("##explorer");
        }

        @Override
        public boolean canProceed() {
            return tmpNamedQueue.size() < MAX_TMPQUEUE_SIZE && dozeUntilSec < nowSec();
        }

        @Override
        public void step() {
            int         tranche = getTranche();
            JsonElement json    = getJsonForTranche(tranche);
            if (json != null) {
                cutTrancheUp(tranche, json);
            }
        }

        public boolean isRateLimited() {
            return dozeUntilSec - nowSec() > 60; // doze time must still be at least a minute
        }

        private int getTranche() {
            return random
                    .ints(0, U.MAX_REPO_ID)
                    .filter(i -> getAll().map(r -> (Repo) r).noneMatch(r -> U.inRange(i, r.getId(), i + TRANCHE_SIZE)))
                    .findFirst().orElseThrow();
        }

        private JsonElement getJsonForTranche(int tranche) {
            String pre = pre(tranche);
            try {
                System.err.println(pre + "go fetch");
                HttpURLConnection con = (HttpURLConnection) new URL(String.format(U.GITHUB_API_URL, tranche)).openConnection();
                if (token != null) {
                    con.setRequestProperty("Authorization", "token " + token);
                }
                con.setRequestProperty("User-Agent", "ModelingValueGroup");
                con.connect();

                long limit  = con.getHeaderFieldLong("X-RateLimit-Limit", -1);
                long remain = con.getHeaderFieldLong("X-RateLimit-Remaining", -1);
                long reset  = con.getHeaderFieldLong("X-RateLimit-Reset", -1) + RATE_LIMIT_RESET_CORRECTION;
                int  code   = con.getResponseCode();

                long now = nowSec();

                if (code == HTTP_OK) {
                    // OK
                    numErrors = 0;
                    JsonElement a = JsonParser.parseString(getContents(con));
                    if (a.isJsonArray()) {
                        int size = a.getAsJsonArray().size();
                        System.err.println(pre + "size=" + size + " (" + remain + " calls left of " + limit + ")");
                        if (size != 0) {
                            return a;
                        }
                    } else {
                        numErrors++;
                        System.err.println(pre + "OK but no JSON array (remain=" + remain + " errors=" + numErrors + ")");
                    }
                } else if (code == HTTP_FORBIDDEN) {
                    // Forbidden, probably rate limited
                    numErrors = 0;
                    dozeUntilSec = reset + 60;
                    if (remain != 0 || dozeUntilSec <= now) {
                        dozeUntilSec = now + 15;
                    }
                    System.err.println(pre + "limit exceeded: doze until " + readableDateTime(dozeUntilSec) + " ms");
                } else {
                    numErrors++;
                    System.err.println(pre + "ERROR: unexpected return code=" + code);
                }
            } catch (IOException e) {
                numErrors++;
                System.err.println(pre + "ERROR: encountered a problem (" + e.getMessage() + ")");
            }
            if (0 < numErrors) {
                dozeUntilSec = nowSec() + numErrors * 10;
            }
            return null;
        }

        private long nowSec() {
            return LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
        }

        private void cutTrancheUp(int tranche, JsonElement json) {
            Set<Integer> foundIds = StreamSupport.stream(((Iterable<JsonElement>) () -> json.getAsJsonArray().iterator()).spliterator(), false)
                    .map(e -> (JsonObject) e)
                    .map(NamedRepo::new)
                    .peek(tmpNamedQueue::putNoExc)
                    .map(Repo::getId)
                    .collect(Collectors.toSet());
            int maxId = foundIds.stream()
                    .mapToInt(i -> i)
                    .max()
                    .orElse(tranche);
            long numNoRepo = IntStream.rangeClosed(tranche, maxId)
                    .filter(i -> !foundIds.contains(i))
                    .mapToObj(NoRepo::new)
                    .peek(absentQueue::putNoExc)
                    .count();

            System.err.println(pre(tranche) + " " + foundIds.size() + " repos, " + numNoRepo + " norepos");
        }

        private String pre(int tranche) {
            return String.format("EXP tranche[%10d]: ", tranche);
        }
    }

    private class HasWorkflowSampler extends PQueueActor<NamedRepo> {
        private final int i;

        public HasWorkflowSampler(int i) {
            super("##HasWorkflowSampler[" + i + "]", tmpNamedQueue);
            this.i = i;
        }

        @Override
        public void step(NamedRepo repo) {
            int    id   = repo.getId();
            String name = repo.getName();
            String url  = String.format(GITHUB_ACTIONS_URL, name);
            try {
                //System.err.println(pre() + "checking out " + name);
                HttpURLConnection con  = (HttpURLConnection) new URL(url).openConnection();
                int               code = con.getResponseCode();
                if (code == HTTP_NOT_FOUND || code == HTTP_UNAUTHORIZED) {
                    //System.err.println(pre() + "nope wf in " + name);
                    nowfQueue.add(new NoWfRepo(id, name));
                } else {
                    System.err.println(pre() + "yeah wf in " + name);
                    wfQueue.add(new WfRepo(id, name));
                }
            } catch (IOException e) {
                System.err.println(pre() + "ERROR: can not determine if workflows on: "+url+" (" + e.getMessage()+")");
            }
        }

        private String pre() {
            return String.format("HAS [%2d]: ", i);
        }
    }

    private class WfsGetter extends PQueueActor<WfRepo> {
        private final int i;

        public WfsGetter(int i) {
            super("##WfsGetter" + i, wfQueue);
            this.i = i;
        }

        @Override
        public void step(WfRepo r) {
            String name = r.getName();
            try {
                System.err.println(pre() + "downloading: " + name);
                List<String>       log           = new ArrayList<>();
                List<WorkFlowFile> workFlowFiles = GitCommandRunner.downloadWorkFlows(name, log);
                if (workFlowFiles.isEmpty()) {
                    // no wfs after all....
                    System.err.println(pre() + "oops no workflows: " + name);
                    nowfQueue.add(new NoWfRepo(r.getId(), name));
                } else {
                    System.err.println(pre() + "YES " + workFlowFiles.size() + " workflows: " + name);
                    gotWfsQueue.add(new GotWfsRepo(r.getId(), name, workFlowFiles));
                }
            } catch (Throwable e) {
                System.err.println(pre() + "ERROR: " + name + ": " + e.getMessage());
                errorsQueue.add(new ErrorRepo(r.getId(), name, e.getMessage()));
            }
        }

        private String pre() {
            return String.format("GET [%2d]: ", i);
        }
    }
}
