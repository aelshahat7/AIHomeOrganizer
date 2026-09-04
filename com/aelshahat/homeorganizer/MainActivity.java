package com.aelshahat.homeorganizer;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class MainActivity extends Activity {
    private TextView status, launcher, summary, report, planView;
    private Button scan, classify, approve, execute, copyReport;
    private List<HomeShortcut> shortcuts=new ArrayList<>();
    private List<ClassificationResult> classifications=new ArrayList<>();
    private OrganizationPlan plan;
    private boolean approved;
    private OrganizationExecutor executor;

    @Override protected void onCreate(Bundle state){super.onCreate(state);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,20,28,20);
        TextView title=new TextView(this);title.setText("AI Home Organizer\ncrDroid Launcher3 + ROOT");title.setTextSize(24);title.setGravity(Gravity.CENTER);root.addView(title,lp());
        status=new TextView(this);status.setTextSize(16);status.setPadding(0,14,0,6);root.addView(status,lp());
        launcher=new TextView(this);root.addView(launcher,lp());
        Button settings=new Button(this);settings.setText("Enable Accessibility Service");settings.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));root.addView(settings,lp());
        Button rootTest=new Button(this);rootTest.setText("TEST ROOT + LAUNCHER3 PROVIDER");rootTest.setOnClickListener(v->testRootBridge());root.addView(rootTest,lp());
        scan=new Button(this);scan.setText("SCAN ALL HOME PAGES");scan.setOnClickListener(v->startScan());root.addView(scan,lp());
        summary=new TextView(this);summary.setPadding(0,10,0,6);root.addView(summary,lp());
        classify=new Button(this);classify.setText("CLASSIFY ON-DEVICE");classify.setEnabled(false);classify.setOnClickListener(v->buildPlan());root.addView(classify,lp());
        TextView pt=new TextView(this);pt.setText("Proposed Organization Plan");pt.setTextSize(16);root.addView(pt,lp());
        ScrollView ps=new ScrollView(this);planView=new TextView(this);planView.setTextSize(14);planView.setPadding(0,8,0,8);ps.addView(planView,new ViewGroup.LayoutParams(-1,-2));root.addView(ps,new LinearLayout.LayoutParams(-1,0,1f));
        approve=new Button(this);approve.setText("APPROVE PLAN");approve.setEnabled(false);approve.setOnClickListener(v->{approved=true;if(plan!=null)plan.setApproved(true);append("USER_APPROVED\n");status.setText("Plan approved. Ready to execute safely, category by category.");updateButtons();});root.addView(approve,lp());
        execute=new Button(this);execute.setText("EXECUTE APPROVED PLAN");execute.setEnabled(false);execute.setOnClickListener(v->executePlan());root.addView(execute,lp());
        LinearLayout bar=new LinearLayout(this);bar.setOrientation(LinearLayout.HORIZONTAL);TextView rt=new TextView(this);rt.setText("Diagnostic Report");rt.setTextSize(16);bar.addView(rt,new LinearLayout.LayoutParams(0,-2,1));copyReport=new Button(this);copyReport.setText("COPY");copyReport.setOnClickListener(v->copyReport());bar.addView(copyReport,new LinearLayout.LayoutParams(-2,-2));root.addView(bar,lp());
        ScrollView rs=new ScrollView(this);report=new TextView(this);report.setTextSize(12);report.setTextIsSelectable(true);report.setPadding(0,8,0,24);rs.addView(report,new ViewGroup.LayoutParams(-1,-2));root.addView(rs,new LinearLayout.LayoutParams(-1,0,1.5f));
        setContentView(root);refresh();
    }
    private LinearLayout.LayoutParams lp(){return new LinearLayout.LayoutParams(-1,-2);}
    @Override protected void onResume(){super.onResume();refresh();}

    private void testRootBridge(){status.setText("Testing Magisk su paths + Launcher3 provider...");new Thread(()->{RootController rc=new RootController();RootController.Result d=rc.diagnoseSuPaths();RootController.Result r=rc.test();RootController.Result p=r.ok()?new RootLauncherController(rc).probe():r;runOnUiThread(()->{append("SU_PATH_DIAGNOSTIC exit="+d.exitCode+" output="+safe(d.output)+"\n");append("ROOT_TEST exit="+r.exitCode+" su="+safe(r.suPath)+" output="+safe(r.output)+"\n");append("LAUNCHER3_PROVIDER_TEST exit="+p.exitCode+" su="+safe(p.suPath)+" output="+safe(p.output)+"\n");status.setText(r.ok()?(p.ok()?"ROOT OK + Launcher3 Provider OK":"ROOT OK, Provider call failed"):("ROOT unavailable: "+r.output));refresh();});}).start();}

    private void startScan(){HomeAccessibilityService s=HomeAccessibilityService.getInstance();if(s==null){status.setText("Service OFF. Enable Accessibility first.");return;}approved=false;plan=null;classifications.clear();status.setText("Scanning every Home Screen page...");s.scanHomeScreen(code->runOnUiThread(()->{refresh();status.setText(code+" | review the detected shortcuts");classify.setEnabled("MULTI_PAGE_SCAN_COMPLETE".equals(code)||"PAGE_LIMIT_REACHED".equals(code));}));}
    private void buildPlan(){HomeAccessibilityService s=HomeAccessibilityService.getInstance();if(s==null||shortcuts.isEmpty())return;classifications=new ClassificationEngine(this).classify(shortcuts);plan=new OrganizationPlan();for(ClassificationResult r:classifications)plan.add(new OrganizationPlan.Item(r.shortcut,r.category,r.confidence,r.reason));approved=false;renderPlan();append("CLASSIFICATION_COMPLETE count="+classifications.size()+"\n");appendClassificationDiagnostics();append("PLAN_READY eligible="+plan.eligibleItems().size()+"\n");status.setText("Plan ready. Review it, then explicitly approve execution.");updateButtons();}
    private void executePlan(){HomeAccessibilityService s=HomeAccessibilityService.getInstance();if(s==null||plan==null||!approved||executor!=null&&executor.isRunning()){status.setText("Execution unavailable or already running.");return;}execute.setEnabled(false);approve.setEnabled(false);scan.setEnabled(false);classify.setEnabled(false);executor=new OrganizationExecutor(s);status.setText("Executing approved plan safely...");executor.execute(plan,r->runOnUiThread(()->{status.setText("ORGANIZATION COMPLETE | success="+r.success+" failed="+r.failed+" skipped="+r.skipped+" unverified="+r.unverified);append("ORGANIZATION_RESULT success="+r.success+" failed="+r.failed+" skipped="+r.skipped+" unverified="+r.unverified+"\n");refresh();}));}
    private void appendClassificationDiagnostics(){append("CLASSIFICATION_RESULTS_BEGIN count="+classifications.size()+"\n");for(ClassificationResult r:classifications)append("CLASSIFICATION_RESULT label="+safe(r.shortcut.label)+" category="+safe(r.category)+" confidence="+String.format(java.util.Locale.US,"%.2f",r.confidence)+" resolvedPackage="+safe(r.resolvedPackage)+" resolution="+(r.resolutionUnique?"unique":(r.resolvedPackage.isEmpty()?"unresolved":"ambiguous"))+" reason="+safe(r.reason)+"\n");append("CLASSIFICATION_RESULTS_END\n");}
    private void renderPlan(){if(plan==null){planView.setText("No plan yet.");return;}StringBuilder b=new StringBuilder("PROPOSED ORGANIZATION PLAN\n");for(Map.Entry<String,List<OrganizationPlan.Item>> e:plan.grouped().entrySet()){b.append("\n").append(e.getKey()).append(" (").append(e.getValue().size()).append(")\n");for(OrganizationPlan.Item i:e.getValue())b.append("  - ").append(i.shortcut.label).append(" | page=").append(i.shortcut.pageIndex).append(" | cell=").append(i.shortcut.cellX).append(",").append(i.shortcut.cellY).append(" | confidence=").append(String.format(java.util.Locale.US,"%.2f",i.confidence)).append("\n");}planView.setText(b.toString());}
    private void copyReport(){String text=report==null?"":report.getText().toString();ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);if(cm!=null){cm.setPrimaryClip(ClipData.newPlainText("AI Home Organizer Diagnostic Report",text));Toast.makeText(this,"Report copied",Toast.LENGTH_SHORT).show();}}
    private void refresh(){HomeAccessibilityService s=HomeAccessibilityService.getInstance();launcher.setText("Launcher: "+(s==null||s.getLastLauncherPackage()==null?"not scanned":s.getLastLauncherPackage())+" | Adapter: "+(s==null||s.getLastAdapterName()==null?"-":s.getLastAdapterName()));List<HomeShortcut> all=s==null?new ArrayList<>():new ArrayList<>(s.getShortcuts());shortcuts=new ArrayList<>();int hot=0;for(HomeShortcut h:all){if(h.hotseat)hot++;else shortcuts.add(h);}int pages=s==null?0:s.getPages().size();summary.setText("Pages detected: "+pages+"\nRegular shortcuts: "+shortcuts.size()+"\nHotseat instances: "+hot);report.setText(s==null?"Service OFF":s.getSavedReport());if(plan!=null)renderPlan();copyReport.setEnabled(report.length()>0);if(status.getText()==null||status.getText().length()==0)status.setText(s==null?"Service OFF":"Service ON");updateButtons();}
    private void updateButtons(){HomeAccessibilityService s=HomeAccessibilityService.getInstance();boolean busy=s!=null&&s.isScanning()||executor!=null&&executor.isRunning();scan.setEnabled(!busy);classify.setEnabled(!busy&&!shortcuts.isEmpty());approve.setEnabled(!busy&&plan!=null&&!approved);execute.setEnabled(!busy&&plan!=null&&approved&&!plan.isEmpty());}
    private void append(String text){HomeAccessibilityService s=HomeAccessibilityService.getInstance();if(s!=null)s.appendDiagnostic(text);}
    private String safe(String s){return s==null?"":s.replace('\n',' ').replace('\r',' ');}
}
