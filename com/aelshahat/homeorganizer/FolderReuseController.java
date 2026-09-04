package com.aelshahat.homeorganizer;

import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.List;

/** Reuses an already-created FolderIcon only when it is explicitly visible in the tree. */
public final class FolderReuseController {
    public interface Callback { void onResult(String code, String detail); }
    private final HomeAccessibilityService service;
    private final Handler handler = new Handler(Looper.getMainLooper());
    public FolderReuseController(HomeAccessibilityService service) { this.service=service; }

    public void add(HomeShortcut item, int expectedPage, int folderX, int folderY, Callback cb) {
        if (item == null || item.hotseat || item.pageIndex != expectedPage) { cb.onResult("FOLDER_ITEM_UNVERIFIED","Unsafe source/page"); return; }
        service.navigateToPage(expectedPage, page -> {
            if (!"PAGE_READY".equals(page)) { cb.onResult("FOLDER_ITEM_UNVERIFIED",page); return; }
            locateAndDrag(item, folderX, folderY, cb, 0);
        });
    }

    private void locateAndDrag(HomeShortcut wanted, int folderX, int folderY, Callback cb, int attempt) {
        handler.postDelayed(() -> {
            AccessibilityNodeInfo root=service.getRootInActiveWindow();
            if(root==null){if(attempt<2){locateAndDrag(wanted,folderX,folderY,cb,attempt+1);return;}cb.onResult("FOLDER_ITEM_UNVERIFIED","Launcher root unavailable");return;}
            String pkg=String.valueOf(root.getPackageName());
            List<HomeShortcut> live=LauncherAdapter.forPackage(pkg).findShortcuts(root,pkg,wanted.pageIndex);
            HomeShortcut source=find(live,wanted);
            boolean folder=hasFolderNear(root,folderX,folderY,180);
            root.recycle();
            service.appendDiagnostic("FOLDER_REUSE_PRECHECK item="+wanted.label+" source="+(source!=null)+" folder="+folder+" page="+wanted.pageIndex+"\n");
            if(source==null || !folder){if(attempt<2){locateAndDrag(wanted,folderX,folderY,cb,attempt+1);return;}cb.onResult("FOLDER_ITEM_UNVERIFIED","Live source or FolderIcon not reliably visible");return;}
            service.performFolderGesture(source.centerX,source.centerY,folderX,folderY,new GestureController.Callback(){
                @Override public void onSuccess(){verify(wanted,folderX,folderY,cb,0);}
                @Override public void onFailure(String reason){cb.onResult("FOLDER_ITEM_FAILED",reason);}
            });
        },attempt==0?500:500);
    }

    private void verify(HomeShortcut wanted,int folderX,int folderY,Callback cb,int attempt){
        handler.postDelayed(()->{
            AccessibilityNodeInfo root=service.getRootInActiveWindow();
            if(root==null){cb.onResult("FOLDER_ITEM_UNVERIFIED","Launcher root unavailable during verification");return;}
            boolean item=containsLabel(root,wanted.label); boolean folder=hasFolderNear(root,folderX,folderY,180); root.recycle();
            service.appendDiagnostic("FOLDER_REUSE_VERIFY itemVisible="+item+" folderVisible="+folder+" attempt="+(attempt+1)+"\n");
            if(folder && !item){cb.onResult("FOLDER_ITEM_ADDED","Item disappeared into verified folder target");return;}
            if(attempt<2){verify(wanted,folderX,folderY,cb,attempt+1);return;}
            cb.onResult("FOLDER_ITEM_UNVERIFIED","Folder remained uncertain or item remained visible");
        },attempt==0?1400:500);
    }

    private HomeShortcut find(List<HomeShortcut> list,HomeShortcut wanted){for(HomeShortcut s:list)if(!s.hotseat&&s.label.equalsIgnoreCase(wanted.label)&&Math.abs(s.centerX-wanted.centerX)<140&&Math.abs(s.centerY-wanted.centerY)<140)return s;return null;}
    private boolean hasFolderNear(AccessibilityNodeInfo n,int x,int y,int r){if(n==null)return false;android.graphics.Rect b=new android.graphics.Rect();n.getBoundsInScreen(b);String q=norm(n.getClassName())+" "+norm(n.getViewIdResourceName())+" "+norm(n.getContentDescription());if(q.contains("folder")&&(Math.abs(b.centerX()-x)<=r&&Math.abs(b.centerY()-y)<=r))return true;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);boolean h=hasFolderNear(c,x,y,r);if(c!=null)c.recycle();if(h)return true;}return false;}
    private boolean containsLabel(AccessibilityNodeInfo n,String label){if(n==null)return false;if(norm(n.getText()).equals(norm(label))||norm(n.getContentDescription()).equals(norm(label)))return true;for(int i=0;i<n.getChildCount();i++){AccessibilityNodeInfo c=n.getChild(i);boolean h=containsLabel(c,label);if(c!=null)c.recycle();if(h)return true;}return false;}
    private String norm(CharSequence s){return s==null?"":s.toString().replace("\u200B","").replace("\u200C","").replace("\u200D","").replace("\uFEFF","").trim().toLowerCase(java.util.Locale.ROOT);}
}
