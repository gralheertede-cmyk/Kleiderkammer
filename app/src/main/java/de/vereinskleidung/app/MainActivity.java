package de.vereinskleidung.app;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
    LinearLayout root, content;
    Api api;
    ArrayList<JSONObject> members = new ArrayList<>();
    ArrayList<JSONObject> items = new ArrayList<>();
    ArrayList<JSONObject> loans = new ArrayList<>();
    String selectedMemberId = "";
    int blue = Color.rgb(36,87,166);

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        api = new Api(this);
        showLogin();
    }

    TextView title(String s) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(24); t.setTextColor(Color.DKGRAY);
        t.setPadding(0,0,0,24); return t;
    }
    Button btn(String s) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); return b;
    }
    EditText edit(String hint) {
        EditText e = new EditText(this); e.setHint(hint); e.setPadding(16,8,16,8); return e;
    }
    void base(String heading) {
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28,30,28,20);
        ScrollView sv = new ScrollView(this);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.addView(title(heading)); sv.addView(content); root.addView(sv, new LinearLayout.LayoutParams(-1,0,1));
        setContentView(root);
    }
    void nav() {
        LinearLayout n = new LinearLayout(this); n.setOrientation(LinearLayout.HORIZONTAL);
        String[] a={"Ausgabe","Rückgabe","Übersicht","Bestand"};
        for(String s:a){ Button b=btn(s); b.setOnClickListener(v->{ if(s.equals("Ausgabe")) showIssue(); else if(s.equals("Rückgabe")) showReturn(); else if(s.equals("Übersicht")) showOverview(); else showStock(); }); n.addView(b,new LinearLayout.LayoutParams(0,58,1));}
        root.addView(n);
    }
    void showLogin() {
        base("Vereinskleidung");
        content.addView(new TextView(this){{setText("Gemeinsamer Vereinszugang"); setTextSize(17);}});
        EditText url=edit("Google-Apps-Script-URL");
        EditText pw=edit("Vereins-Passwort"); pw.setInputType(0x81);
        content.addView(url); content.addView(pw);
        Button b=btn("Anmelden");
        content.addView(b);
        String oldUrl=getPreferences(0).getString("url","");
        url.setText(oldUrl);
        b.setOnClickListener(v->{
            api.url=url.getText().toString().trim(); api.password=pw.getText().toString();
            if(api.url.isEmpty()||api.password.isEmpty()){toast("URL und Passwort eingeben");return;}
            getPreferences(0).edit().putString("url",api.url).apply();
            b.setEnabled(false);
            api.call("summary",new JSONObject(),r->{runOnUiThread(()->{b.setEnabled(true); if(r.optBoolean("ok")){load();} else toast(r.optString("error","Anmeldung fehlgeschlagen"));});});
        });
    }
    void load(){ api.call("bootstrap",new JSONObject(),r->runOnUiThread(()->{if(!r.optBoolean("ok")){toast(r.optString("error"));return;} members=toList(r.optJSONArray("members")); items=toList(r.optJSONArray("items")); loans=toList(r.optJSONArray("loans")); showHome();}));}
    ArrayList<JSONObject> toList(JSONArray a){ArrayList<JSONObject> x=new ArrayList<>(); if(a!=null)for(int i=0;i<a.length();i++)x.add(a.optJSONObject(i));return x;}

    void showHome(){base("Vereinskleidung"); content.addView(new TextView(this){{setText("Was möchtest du tun?");setTextSize(18);}});
        Button a=btn("➕ Kleidung ausgeben"); a.setOnClickListener(v->showIssue()); content.addView(a);
        Button b=btn("↩ Kleidung zurücknehmen"); b.setOnClickListener(v->showReturn()); content.addView(b);
        Button c=btn("👥 Wer hat was?"); c.setOnClickListener(v->showOverview()); content.addView(c);
        Button d=btn("📦 Bestand"); d.setOnClickListener(v->showStock()); content.addView(d);
        Button e=btn("⚙ Verwaltung / Inventur"); e.setOnClickListener(v->showAdmin()); content.addView(e);
    }
    ArrayAdapter<String> memberAdapter(){ArrayList<String>a=new ArrayList<>();for(JSONObject m:members)a.add(m.optString("name"));return new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,a);}
    void showIssue(){
        base("Kleidung ausgeben"); nav();
        Spinner sp=new Spinner(this); sp.setAdapter(memberAdapter()); content.addView(sp);
        TextView hint=new TextView(this); hint.setText("Kleidungsstück"); hint.setTextSize(17); content.addView(hint);
        String[] types={"T-Shirt","Trikot","Trikothose","Trainingsjacke","Trainingshose"};
        Spinner typ=new Spinner(this); typ.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types)); content.addView(typ);
        EditText size=edit("Größe (z. B. S, M, L, XL)"); content.addView(size);
        EditText number=edit("Nummer (optional)"); content.addView(number);
        Button save=btn("Ausgabe speichern"); content.addView(save);
        save.setOnClickListener(v->{
            if(sp.getSelectedItemPosition()<0||size.getText().toString().trim().isEmpty()){toast("Person und Größe auswählen");return;}
            JSONObject m=members.get(sp.getSelectedItemPosition());
            JSONObject p=new JSONObject(); try{p.put("memberId",m.optString("id"));p.put("type",typ.getSelectedItem().toString());p.put("size",size.getText().toString().trim());p.put("number",number.getText().toString().trim());}catch(Exception ignored){}
            api.call("issue",p,r->runOnUiThread(()->{if(r.optBoolean("ok")){toast("Ausgabe gespeichert");load();}else toast(r.optString("error","Nicht möglich"));}));
        });
    }
    void showReturn(){
        base("Rückgabe"); nav();
        Spinner sp=new Spinner(this); sp.setAdapter(memberAdapter()); content.addView(sp);
        LinearLayout list=new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); content.addView(list);
        Button find=btn("Ausgeliehene Sachen anzeigen"); content.addView(find);
        find.setOnClickListener(v->{list.removeAllViews(); String id=members.get(sp.getSelectedItemPosition()).optString("id"); for(JSONObject l:loans)if(l.optString("memberId").equals(id)){CheckBox c=new CheckBox(this);c.setText(l.optString("type")+"  "+l.optString("size")+(l.optString("number").isEmpty()?"":"  Nr. "+l.optString("number")));c.setTag(l.optString("itemId"));list.addView(c);} Button ret=btn("Markierte zurückgeben");list.addView(ret);ret.setOnClickListener(x->{JSONArray ids=new JSONArray();for(int i=0;i<list.getChildCount();i++){View q=list.getChildAt(i);if(q instanceof CheckBox && ((CheckBox)q).isChecked())ids.put(q.getTag().toString());}JSONObject p=new JSONObject();try{p.put("itemIds",ids);}catch(Exception ignored){}api.call("return",p,r->runOnUiThread(()->{if(r.optBoolean("ok")){toast("Rückgabe gespeichert");load();}else toast(r.optString("error"));}));});});
    }
    void showOverview(){
        base("Wer hat was?"); nav();
        for(JSONObject m:members){String id=m.optString("id");StringBuilder s=new StringBuilder(m.optString("name")+": ");boolean any=false;for(JSONObject l:loans)if(l.optString("memberId").equals(id)){if(any)s.append(", ");s.append(l.optString("type")).append(" ").append(l.optString("size"));if(!l.optString("number").isEmpty())s.append(" / Nr. ").append(l.optString("number"));any=true;} if(!any)s.append("nichts ausgeliehen"); TextView t=new TextView(this);t.setText(s.toString());t.setTextSize(16);t.setPadding(0,8,0,8);content.addView(t);}
    }
    void showStock(){
        base("Bestand"); nav();
        HashMap<String,int[]> map=new HashMap<>();
        for(JSONObject i:items){String k=i.optString("type")+" | "+i.optString("size");int[] a=map.get(k);if(a==null){a=new int[2];map.put(k,a);}a[0]++;if(i.optString("status").equals("available"))a[1]++;}
        for(String k:new TreeSet<>(map.keySet())){int[]a=map.get(k);TextView t=new TextView(this);t.setText(k+"   Gesamt: "+a[0]+"   Verfügbar: "+a[1]+"   Ausgegeben: "+(a[0]-a[1]));t.setTextSize(16);t.setPadding(0,8,0,8);content.addView(t);}
        content.addView(btn("➕ Bestand ergänzen")).setOnClickListener(v->showAddStock());
    }
    void showAddStock(){
        base("Bestand ergänzen"); nav();
        String[] types={"T-Shirt","Trikot","Trikothose","Trainingsjacke","Trainingshose"};
        Spinner typ=new Spinner(this);typ.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,types));content.addView(typ);
        EditText size=edit("Größe");EditText nums=edit("Nummern, z. B. 10-19 oder 10,11,12 (leer = ohne Nummer)");content.addView(size);content.addView(nums);
        EditText count=edit("Anzahl bei nicht nummerierten Sachen");count.setInputType(2);content.addView(count);
        Button b=btn("In Bestand aufnehmen");content.addView(b);
        b.setOnClickListener(v->{JSONObject p=new JSONObject();try{p.put("type",typ.getSelectedItem().toString());p.put("size",size.getText().toString().trim());p.put("numbers",nums.getText().toString().trim());p.put("count",count.getText().toString().trim());}catch(Exception ignored){}api.call("addStock",p,r->runOnUiThread(()->{if(r.optBoolean("ok")){toast("Bestand ergänzt");load();}else toast(r.optString("error"));}));});
    }
    void showAdmin(){
        base("Verwaltung / Inventur"); nav();
        EditText name=edit("Neues Mitglied");content.addView(name);Button add=btn("Mitglied hinzufügen");content.addView(add);
        add.setOnClickListener(v->{JSONObject p=new JSONObject();try{p.put("name",name.getText().toString().trim());}catch(Exception ignored){}api.call("addMember",p,r->runOnUiThread(()->{toast(r.optBoolean("ok")?"Mitglied hinzugefügt":r.optString("error"));if(r.optBoolean("ok"))load();}));});
        content.addView(new TextView(this){{setText("\nMitglieder");setTextSize(18);}});
        for(JSONObject m:members){TextView t=new TextView(this);t.setText("• "+m.optString("name"));t.setTextSize(16);t.setPadding(0,6,0,6);content.addView(t);}
        Button refresh=btn("Daten aktualisieren");content.addView(refresh);refresh.setOnClickListener(v->load());
    }
    void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
}
class Api {
    android.content.Context ctx; String url="",password="";
    Api(android.content.Context c){ctx=c;}
    interface CB{void ok(JSONObject r);}
    void call(String action, JSONObject data, CB cb){
        new Thread(()->{
            try{
                data.put("action",action);data.put("password",password);
                HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
                c.setRequestMethod("POST");c.setConnectTimeout(12000);c.setReadTimeout(20000);c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");
                OutputStream o=c.getOutputStream();o.write(data.toString().getBytes("UTF-8"));o.close();
                InputStream in=(c.getResponseCode()<400?c.getInputStream():c.getErrorStream());
                BufferedReader br=new BufferedReader(new InputStreamReader(in));StringBuilder s=new StringBuilder();String line;while((line=br.readLine())!=null)s.append(line);
                cb.ok(new JSONObject(s.toString()));
            }catch(Exception e){try{JSONObject r=new JSONObject();r.put("ok",false);r.put("error","Netzwerkfehler: "+e.getMessage());cb.ok(r);}catch(Exception ignored){}}
        }).start();
    }
}
