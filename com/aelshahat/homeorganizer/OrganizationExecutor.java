package com.aelshahat.homeorganizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Sequential, approval-gated executor for the crDroid Launcher3 drag/drop bridge. */
public final class OrganizationExecutor {
    public interface Callback { void onFinished(Report report); }
    public static final class Entry { public final String category,source,target,status,detail; Entry(String c,String s,String t,String st,String d){category=c;source=s;target=t;status=st;detail=d;} }
    public static final class Report { public final List<Entry> entries=new ArrayList<>(); public int success,skipped,failed,unverified; }
    private final HomeAccessibilityService service;
    private final FolderReuseController reuse;
    private boolean running;
    public OrganizationExecutor(HomeAccessibilityService service){this.service=service;this.reuse=new FolderReuseController(service);}
    public boolean isRunning(){return running;}
    public void execute(final OrganizationPlan plan,final Callback callback){
        final Report r=new Report();
        if(running){r.entries.add(new Entry("","","","FAILED","Executor already running"));r.failed++;callback.onFinished(r);return;}
        if(service==null||plan==null||!plan.isApproved()){r.entries.add(new Entry("","","","SKIPPED","Plan is missing or not approved"));r.skipped++;callback.onFinished(r);return;}
        running=true;service.appendDiagnostic("EXECUTION_START approved=true\n");executeGroup(buildGroups(plan),0,r,callback);
    }
    private static final class Group{final String category;final List<OrganizationPlan.Item> items;Group(String c,List<OrganizationPlan.Item> i){category=c;items=i;}}
    private List<Group> buildGroups(OrganizationPlan p){List<Group> gs=new ArrayList<>();for(Map.Entry<String,List<OrganizationPlan.Item>> e:p.grouped().entrySet()){List<OrganizationPlan.Item> is=new ArrayList<>();for(OrganizationPlan.Item i:e.getValue())if(!i.shortcut.hotseat&&!"Needs Review".equalsIgnoreCase(i.category))is.add(i);is.sort(Comparator.comparingDouble((OrganizationPlan.Item i)->-i.confidence));gs.add(new Group(e.getKey(),is));}return gs;}
    private void executeGroup(List<Group> gs,int index,Report r,Callback cb){
        if(index>=gs.size()){finish(r,cb);return;} Group g=gs.get(index);
        if(g.items.size()<2){for(OrganizationPlan.Item i:g.items){r.skipped++;r.entries.add(new Entry(g.category,i.shortcut.label,"","SKIPPED","Category has fewer than two items"));}executeGroup(gs,index+1,r,cb);return;}
        OrganizationPlan.Item anchor=g.items.get(0),seed=findSamePage(anchor,g.items);
        if(seed==null){for(OrganizationPlan.Item i:g.items){r.skipped++;r.entries.add(new Entry(g.category,i.shortcut.label,"","SKIPPED","No safe same-page pair available for folder creation"));}executeGroup(gs,index+1,r,cb);return;}
        service.appendDiagnostic("OPERATION_START category="+g.category+" source="+seed.shortcut.label+" target="+anchor.shortcut.label+" page="+anchor.shortcut.pageIndex+" cell="+seed.shortcut.cellX+","+seed.shortcut.cellY+" -> "+anchor.shortcut.cellX+","+anchor.shortcut.cellY+" sourceCenter="+seed.shortcut.centerX+","+seed.shortcut.centerY+" targetCenter="+anchor.shortcut.centerX+","+anchor.shortcut.centerY+"\n");
        service.createFolder(seed.shortcut,anchor.shortcut,(code,detail)->{
            String st="FOLDER_CREATED".equals(code)?"SUCCESS":(code.contains("VERIFICATION")?"UNVERIFIED":"FAILED");count(r,st);r.entries.add(new Entry(g.category,seed.shortcut.label,anchor.shortcut.label,st,detail));
            if(!"SUCCESS".equals(st)){for(OrganizationPlan.Item i:g.items)if(i!=seed&&i!=anchor){r.skipped++;r.entries.add(new Entry(g.category,i.shortcut.label,anchor.shortcut.label,"SKIPPED","Folder state is not safely established"));}executeGroup(gs,index+1,r,cb);return;}
            addRemaining(g,seed,anchor,0,r,()->executeGroup(gs,index+1,r,cb));
        });
    }
    private void addRemaining(Group g,OrganizationPlan.Item seed,OrganizationPlan.Item anchor,int pos,Report r,Runnable done){
        if(pos>=g.items.size()){done.run();return;} OrganizationPlan.Item item=g.items.get(pos++);
        if(item==seed||item==anchor){addRemaining(g,seed,anchor,pos,r,done);return;}
        if(item.shortcut.pageIndex!=anchor.shortcut.pageIndex){r.skipped++;r.entries.add(new Entry(g.category,item.shortcut.label,anchor.shortcut.label,"SKIPPED","Different page; cross-page drag into an existing folder is not attempted safely"));addRemaining(g,seed,anchor,pos,r,done);return;}
        reuse.add(item.shortcut,anchor.shortcut.pageIndex,anchor.shortcut.centerX,anchor.shortcut.centerY,result->{String st="FOLDER_ITEM_ADDED".equals(result)?"SUCCESS":("FOLDER_ITEM_UNVERIFIED".equals(result)?"UNVERIFIED":"FAILED");count(r,st);r.entries.add(new Entry(g.category,item.shortcut.label,anchor.shortcut.label,st,"Reuse existing FolderIcon"));if("SUCCESS".equals(st))addRemaining(g,seed,anchor,pos,r,done);else done.run();});
    }
    private void count(Report r,String s){if("SUCCESS".equals(s))r.success++;else if("UNVERIFIED".equals(s))r.unverified++;else r.failed++;}
    private OrganizationPlan.Item findSamePage(OrganizationPlan.Item a,List<OrganizationPlan.Item> items){for(OrganizationPlan.Item i:items)if(i!=a&&i.shortcut.pageIndex==a.shortcut.pageIndex&&!i.shortcut.hotseat)return i;return null;}
    private void finish(Report r,Callback cb){running=false;service.appendDiagnostic("EXECUTION_COMPLETE success="+r.success+" failed="+r.failed+" skipped="+r.skipped+" unverified="+r.unverified+"\n");cb.onFinished(r);}
}
