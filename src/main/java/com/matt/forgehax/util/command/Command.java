package com.matt.forgehax.util.command;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Sets;
import com.google.gson.JsonObject;

import com.matt.forgehax.Helper;
import com.matt.forgehax.util.SafeConverter;
import com.matt.forgehax.util.VariableCompTreeSet;
import com.matt.forgehax.util.command.callbacks.CallbackData;
import com.matt.forgehax.util.command.exception.CommandBuildException;
import com.matt.forgehax.util.command.exception.CommandExecuteException;
import com.matt.forgehax.util.command.exception.CommandParentNonNullException;
import com.matt.forgehax.util.serialization.GsonConstant;
import com.matt.forgehax.util.serialization.ISerializableJson;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.internal.Strings;

/**
 * Created on 5/14/2017 by fr1kin
 */
public class Command implements Comparable<Command>, ISerializableJson, GsonConstant {
  
  public static final String NAME = "Command.name";
  public static final String DESCRIPTION = "Command.description";
  public static final String OPTIONBUILDERS = "Command.optionbuilder";
  public static final String PROCESSORS = "Command.processors";
  public static final String HELP = "Command.help";
  public static final String PARENT = "Command.parent";
  public static final String HELPAUTOGEN = "Command.helpAutoGen";
  public static final String CALLBACKS = "Command.callbacks";
  public static final String REQUIREDARGS = "Command.requiredArgs";
  
  private final String name;
  private final String description;
  
  protected final OptionParser parser = new OptionParser();
  
  protected final Collection<Consumer<ExecuteData>> processors = Lists.newArrayList();
  
  protected final Consumer<ExecuteData> help;
  
  private final VariableCompTreeSet<Command> children = new VariableCompTreeSet<>();
  
  protected final Multimap<CallbackType, Consumer<CallbackData>> callbacks =
      Multimaps.newSetMultimap(Maps.newHashMap(), Sets::newLinkedHashSet);
  
  private final int requiredArgs;
  
  private Command parent;
  
  @SuppressWarnings("unchecked")
  protected Command(Map<String, Object> data) throws CommandBuildException {
    try {
      this.name = (String) data.get(NAME);
      Objects.requireNonNull(this.name, "Command requires name");
      
      this.description = (String) data.getOrDefault(DESCRIPTION, Strings.EMPTY);
      this.help = (Consumer<ExecuteData>) data.get(HELP);
      
      Collection<Consumer<ExecuteData>> processors =
          (Collection<Consumer<ExecuteData>>) data.get(PROCESSORS);
      if (processors != null) {
        this.processors.addAll(processors);
      }
      
      // Set command parent
      Command parent = (Command) data.get(PARENT);
      if (parent != null) {
        parent.addChild(this);
      }
      
      // By default, auto generate help option
      // User must specify not to add it
      Boolean helpAutoGen = (Boolean) data.getOrDefault(HELPAUTOGEN, true);
      if (helpAutoGen) {
        parser.acceptsAll(Arrays.asList("help", "?"), "Help text for options");
      }
      
      // Execute custom option builder (if it exists)
      Collection<Consumer<OptionParser>> optionBuilders =
          (Collection<Consumer<OptionParser>>) data.get(OPTIONBUILDERS);
      if (optionBuilders != null) {
        for (Consumer<OptionParser> c : optionBuilders) {
          c.accept(this.parser);
        }
      }
      
      // Add any callbacks created by the builder
      Multimap<CallbackType, Consumer<CallbackData>> callbacks =
          (Multimap<CallbackType, Consumer<CallbackData>>) data.get(CALLBACKS);
      if (callbacks != null) {
        this.callbacks.putAll(callbacks);
      }

      this.parser.allowsUnrecognizedOptions();
      
      this.requiredArgs = Math.max(SafeConverter.toInteger(data.getOrDefault(REQUIREDARGS, 0)), 0);
    } catch (Throwable t) {
      throw new CommandBuildException("Failed to build command", t);
    }
  }
  
  public boolean isGlobal() {
    return false;
  }
  
  public String getName() {
    return name;
  }

  public int getRequiredArgs() {
    return requiredArgs;
  }
  
  public String[] getHierarchy() {
    List<String> parents = new ArrayList<>();
    Command curr = this.getParent();
    while (curr != null && !curr.isGlobal()) {
      parents.add(curr.getName());
      curr = curr.getParent();
    }
    return (String[]) parents.toArray();
  }

  public String getAbsoluteName() {
    return (getParent() != null && !getParent().isGlobal())
        ? (getParent().getAbsoluteName() + "." + getName())
        : getName();
  }

  public String getRootName() {
    return (getParent() != null && !getParent().isGlobal())
          ? getParent().getRootName()
          : getName();
  }
  
  public String getDescription() {
    return description;
  }
  
  public String getPrintText() {
    return getName() + " - " + getDescription();
  }
  
  public String getOptionHelpText() {
    StringWriter writer = new StringWriter();
    try {
      parser.printHelpOn(writer);
    } catch (IOException e) {
    } finally {
      try {
        writer.close();
      } catch (IOException e) {
      }
    }
    return writer.toString();
  }
  
  @Nullable
  public Command getParent() {
    return parent;
  }
  
  protected void setParent(Command parent) {
    if (this.parent != null && parent != null) {
      throw new CommandParentNonNullException("Command parent already exists");
    }
    this.parent = parent;
  }
  
  public boolean leaveParent() {
    return parent != null && parent.removeChild(this);
  }
  
  public CommandBuilders builders() {
    return CommandBuilders.newInstance(this);
  }
  
  public boolean addChild(@Nonnull Command child) {
    boolean b;
    if (b = children.add(child)) {
      child.setParent(this);
    }
    return b;
  }
  
  public boolean removeChild(@Nonnull Command child) {
    boolean b;
    // if child was removed, set parent to null.
    if (b = children.remove(child)) {
      child.setParent(null);
    }
    return b;
  }
  
  @Nullable
  public Command getChild(String name) {
    for (Command command : children) {
      if (command.getName().equalsIgnoreCase(name)) {
        return command;
      }
    }
    return null;
  }

  @Nullable
  public List<Command> getPossibleChildsDeep(String[] args) {
    List<Command> cmds = null;
    Command curr = this;
    for (String s : args) {
      cmds = curr.getPossibleChilds(s);
      if (cmds.size() != 1) break;
      else curr = cmds.get(0);
    }
    return cmds;
  }

  @Nullable
  public List<Command> getPossibleChilds(String name) {
    return children
          .stream()
          .filter(cmd -> cmd.getName().toLowerCase().startsWith(name.toLowerCase()))
          .collect(Collectors.toList());
  }

  @Nullable
  public Command getClosestChildDeep(String[] args) {
    Command cmd = this;
    for (String s : args) {
      cmd = cmd.getClosestChild(s);
      if (cmd == null) break;
    }
    return cmd;
  }

  @Nullable
  public Command getClosestChild(String name) {
    Command res = getChild(name);
    if (res != null) return res;

    List<Command> results = getPossibleChilds(name);
    if (results.size() == 1) return results.get(0);
    else return null;
  }
  
  public Collection<Command> getChildren() {
	  return Collections.unmodifiableCollection(children);
  }
  
  /*
   * Only call this after the sort type is changed
   */
  public void reOrder(Comparator<Command> comp) {
	  reOrder0(children, comp);
  }
  
  private void reOrder0(VariableCompTreeSet<Command> children, Comparator<Command> comp) {
	  children.reorganizeBasedOn(comp);
	  for(Command c: children) {
		  reOrder0(c.children, comp);
	  }
  }
  
  public void getChildrenDeep(final Collection<Command> all) {
    all.addAll(getChildren());
    children.forEach(child -> child.getChildrenDeep(all));
  }
  
  public Collection<Command> getChildrenDeep() {
    Collection<Command> all = Sets.newHashSet();
    getChildrenDeep(all);
    return all; // does not need to be unmodifiable since we are creating a duplicate containing all
    // children
  }
  
  public void abandonChildren() {
    children.forEach(Command::leaveParent);
  }
  
  @SuppressWarnings("unchecked")
  @Nullable
  protected <T extends CallbackData> Consumer<T> addCallback(
      CallbackType type, Consumer<T> consumer) {
    return callbacks.put(type, (Consumer<CallbackData>) consumer) ? consumer : null;
  }
  
  protected boolean removeCallback(CallbackType type, Consumer<? extends CallbackData> consumer) {
    return callbacks.remove(type, consumer);
  }
  
  protected <T extends CallbackData> void invokeCallbacks(CallbackType type, T data) {
    callbacks.get(type).forEach(c -> c.accept(data));
  }
  
  protected boolean processHelp(ExecuteData data)
      throws CommandExecuteException, NullPointerException {
    if (data.options().has("help")) {
      if (help != null) help.accept(data);
      data.write("");
      data.write(getOptionHelpText());
      return true;
    } else {
      return false;
    }
  }
  
  protected boolean processMain(ExecuteData data)
      throws CommandExecuteException, NullPointerException {
    if (processors != null) {
      for (Consumer<ExecuteData> c : processors) {
        try {
          c.accept(data);
          if (data.isStopped()) {
            break;
          }
        } catch (Throwable t) {
          data.markFailed();
          throw t;
        }
      }
      return true;
    } else {
      return false;
    }
  }
  
  protected boolean processChildren(@Nonnull String[] args)
      throws CommandExecuteException, NullPointerException {
    if (args.length > 0) {
      final String lookup = (args[0] != null ? args[0] : Strings.EMPTY).toLowerCase();
      Command child = getChild(lookup);
      if (child != null) { // perfect match, use this
        child.run(CommandHelper.forward(args));
        return true;
      } else { // no match found, try and infer
        List<Command> results =
            children
                .stream()
                .filter(cmd -> cmd.getName().toLowerCase().startsWith(lookup))
                .collect(Collectors.toList());
        
        if (results.size() == 1) { // if found 1 result, use that
          results.get(0).run(CommandHelper.forward(args));
          return true;
        } else if (results.size() > 1) {
          throw new CommandExecuteException(
              String.format(
                  "Ambiguous command \"%s\": %s",
                  lookup,
                  results.stream().map(Command::getName).collect(Collectors.joining(", "))));
        }
      }
    }
    return false;
  }
  
  protected boolean preprocessor(String[] args) {
    return true;
  }
  
  @SuppressWarnings("Duplicates")
  public void run(@Nonnull String[] args) throws CommandExecuteException, NullPointerException {
    if (!processChildren(args)) { // attempt to match child commands first      
      if (!preprocessor(args)) {
        return;
      }

      OptionSet options = parser.parse(args);
      ExecuteData data = new ExecuteData(this, options, new String[0]);
      
      // only process main if no help was processed
      if (!processHelp(data)) {
        processMain(data);
      }
      
      switch (data.state()) {
        case SUCCESS:
          invokeCallbacks(CallbackType.SUCCESS, new CallbackData(this));
          break;
        case FAILED:
          invokeCallbacks(CallbackType.FAILURE, new CallbackData(this));
          break;
      }
    }
  }

  public void reset_defaults() {
    getChildren().forEach(c -> c.reset_defaults());
  }
  
  @Override
  public void serialize(JsonObject in) {
    JsonObject add = new JsonObject();
    getChildren().forEach(c -> c.serialize(add));
    if (!add.entrySet().isEmpty())
      in.add(getName(), add);
  }
  
  @Override
  public void deserialize(JsonObject in) {
    JsonObject from = in.getAsJsonObject(getName());
    if (from != null)
      getChildren().forEach(c -> c.deserialize(from));
  }
  
  @Override
  public int compareTo(Command o) {
	  return String.CASE_INSENSITIVE_ORDER.compare(getAbsoluteName(), o.getAbsoluteName());
  }
  
  @Override
  public boolean equals(Object o) {
    return o instanceof Command
        && getAbsoluteName().equalsIgnoreCase(((Command) o).getAbsoluteName());
  }
  
  @Override
  public int hashCode() {
    return getAbsoluteName().toLowerCase().hashCode();
  }
  
  @Override
  public String toString() {
    return getAbsoluteName();
  }
}
