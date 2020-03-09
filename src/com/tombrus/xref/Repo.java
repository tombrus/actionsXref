package com.tombrus.xref;

import static java.util.Arrays.*;
import static java.util.Collections.*;

import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.JsonObject;
import com.tombrus.persistentqueues.Recreator;
import com.tombrus.persistentqueues.Savable;

public abstract class Repo implements Savable {
    private final int id;

    public Repo(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return save().stream()
                .map(Object::toString)
                .collect(Collectors.joining(","));
    }

    public static class NoRepo extends Repo implements Comparable<NoRepo> {
        public static final Recreator<NoRepo> RECREATOR = s -> new NoRepo(Integer.parseUnsignedInt(s.get(0)));

        public NoRepo(int id) {
            super(id);
        }

        @Override
        public List<?> save() {
            return singletonList(getId());
        }

        @Override
        public int compareTo(NoRepo o) {
            return Integer.compare(getId(), o.getId());
        }
    }

    public static class NamedRepo extends Repo implements Comparable<NamedRepo> {
        public static final Recreator<NamedRepo> RECREATOR = s -> new NamedRepo(Integer.parseUnsignedInt(s.get(0)), s.get(1));
        private final       String               name;


        public NamedRepo(JsonObject jsonInfo) {
            this(getIdFromJson(jsonInfo), getNameFromJson(jsonInfo));
        }

        public NamedRepo(int id, String name) {
            super(id);
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public List<?> save() {
            return asList(getId(), getName());
        }

        @Override
        public int compareTo(NamedRepo o) {
            return name.compareTo(o.name);
        }
    }

    public static class ErrorRepo extends NamedRepo {
        public static final Recreator<ErrorRepo> RECREATOR = s -> new ErrorRepo(Integer.parseUnsignedInt(s.get(0)), s.get(1), s.get(2));
        private final       String               msg;

        public ErrorRepo(int id, String name, String msg) {
            super(id, name);
            this.msg = msg;
        }

        public String getMsg() {
            return msg;
        }

        @Override
        public List<?> save() {
            return asList(getId(), getName(), getMsg());
        }
    }

    public static class NoWfRepo extends NamedRepo {
        public static final Recreator<NoWfRepo> RECREATOR = s -> new NoWfRepo(Integer.parseUnsignedInt(s.get(0)), s.get(1));

        public NoWfRepo(int id, String name) {
            super(id, name);
        }

        @Override
        public List<?> save() {
            return asList(getId(), getName());
        }
    }

    public static class WfRepo extends NamedRepo {
        public static final Recreator<WfRepo> RECREATOR = s -> new WfRepo(Integer.parseUnsignedInt(s.get(0)), s.get(1));

        public WfRepo(int id, String name) {
            super(id, name);
        }

        @Override
        public List<?> save() {
            return asList(getId(), getName());
        }
    }

    public static class GotWfsRepo extends NamedRepo {
        public static final Recreator<GotWfsRepo> RECREATOR = s -> new GotWfsRepo(Integer.parseUnsignedInt(s.get(0)), s.get(1), U.deserialize(s.get(2), s.get(1)));
        private final       List<WorkFlowFile>    workFlows;

        public GotWfsRepo(int id, String name, List<WorkFlowFile> workFlows) {
            super(id, name);
            this.workFlows = workFlows;
        }

        public List<WorkFlowFile> getWorkFlows() {
            return workFlows;
        }

        @Override
        public List<?> save() {
            return asList(getId(), getName(), U.serialize(getWorkFlows()));
        }
    }

    private static int getIdFromJson(JsonObject jsonInfo) {
        return jsonInfo.get("id").getAsInt();
    }

    private static String getNameFromJson(JsonObject jsonInfo) {
        String fullName = jsonInfo.get("full_name").toString();
        return fullName.substring(1, fullName.length() - 1);
    }
}
