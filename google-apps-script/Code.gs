/*
 * VereinsKleiderverwaltung – Google Apps Script Backend
 *
 * Einrichtung:
 * 1. Neue Google-Tabelle anlegen.
 * 2. Erweiterungen -> Apps Script öffnen.
 * 3. Diesen Code einfügen.
 * 4. Unter Projekteinstellungen -> Skripteigenschaften:
 *      APP_PASSWORD = ein langes Vereins-Passwort
 * 5. Bereitstellen -> Neue Bereitstellung -> Web-App
 *      Ausführen als: Ich
 *      Zugriff: Jeder mit dem Link
 * 6. Die Web-App-URL in die Android-App eintragen.
 *
 * Die Tabelle wird beim ersten Aufruf automatisch mit den benötigten Blättern
 * und Überschriften angelegt.
 */

const SHEETS = {
  MEMBERS: 'Members',
  ITEMS: 'Items',
  LOG: 'Transactions'
};

function doPost(e) {
  const lock = LockService.getScriptLock();
  lock.waitLock(15000);
  try {
    const p = JSON.parse(e.postData.contents || '{}');
    if (!checkPassword_(p.password)) return json_({ok:false,error:'Falsches Passwort'});
    setup_();
    switch (p.action) {
      case 'summary': return json_({ok:true});
      case 'bootstrap': return json_(bootstrap_());
      case 'addMember': return json_(addMember_(p));
      case 'addStock': return json_(addStock_(p));
      case 'issue': return json_(issue_(p));
      case 'return': return json_(returnItems_(p));
      default: return json_({ok:false,error:'Unbekannte Aktion'});
    }
  } catch (err) {
    return json_({ok:false,error:String(err)});
  } finally {
    lock.releaseLock();
  }
}

function doGet(e) {
  return json_({ok:true,service:'VereinsKleiderverwaltung'});
}

function checkPassword_(pw) {
  const expected = PropertiesService.getScriptProperties().getProperty('APP_PASSWORD');
  return !!expected && pw === expected;
}

function setup_() {
  const ss = SpreadsheetApp.getActive();
  ensureSheet_(ss, SHEETS.MEMBERS, ['id','name','active']);
  ensureSheet_(ss, SHEETS.ITEMS, ['id','type','size','number','status','memberId','createdAt']);
  ensureSheet_(ss, SHEETS.LOG, ['id','action','itemId','memberId','timestamp']);
}

function ensureSheet_(ss, name, headers) {
  let sh = ss.getSheetByName(name);
  if (!sh) sh = ss.insertSheet(name);
  if (sh.getLastRow() === 0) sh.appendRow(headers);
}

function rows_(name) {
  const sh = SpreadsheetApp.getActive().getSheetByName(name);
  const values = sh.getDataRange().getValues();
  const headers = values.shift();
  return values.filter(r => r.some(v => v !== '')).map(r => {
    const o = {}; headers.forEach((h,i)=>o[h]=String(r[i] ?? '')); return o;
  });
}

function bootstrap_() {
  const allItems = rows_(SHEETS.ITEMS);
  return {
    ok:true,
    members: rows_(SHEETS.MEMBERS).filter(x=>x.active!=='false'),
    items: allItems,
    loans: allItems.filter(x=>x.status==='loaned')
  };
}

function id_() { return Utilities.getUuid(); }
function now_() { return new Date().toISOString(); }

function addMember_(p) {
  const name = String(p.name||'').trim();
  if (!name) return {ok:false,error:'Name fehlt'};
  SpreadsheetApp.getActive().getSheetByName(SHEETS.MEMBERS).appendRow([id_(),name,'true']);
  return {ok:true};
}

function addStock_(p) {
  const type=String(p.type||'').trim(), size=String(p.size||'').trim();
  if(!type||!size) return {ok:false,error:'Art und Größe fehlen'};
  const nums=String(p.numbers||'').trim();
  let numbers=[];
  if(nums) numbers=parseNumbers_(nums);
  else {
    const count=Math.max(0,parseInt(p.count||'0',10)||0);
    for(let i=0;i<count;i++) numbers.push('');
  }
  if(!numbers.length) return {ok:false,error:'Bitte Nummern oder eine Anzahl angeben'};
  const sh=SpreadsheetApp.getActive().getSheetByName(SHEETS.ITEMS);
  const existing=rows_(SHEETS.ITEMS);
  const keys=new Set(existing.filter(x=>x.type===type).map(x=>x.number));
  for(const n of numbers) {
    if(n && type==='Trikot' && keys.has(n)) return {ok:false,error:'Nummer '+n+' ist bereits im Bestand'};
  }
  numbers.forEach(n=>sh.appendRow([id_(),type,size,n,'available','',now_()]));
  return {ok:true,added:numbers.length};
}

function parseNumbers_(s) {
  const out=[];
  s.split(',').map(x=>x.trim()).filter(Boolean).forEach(part=>{
    const m=part.match(/^(\d+)\s*-\s*(\d+)$/);
    if(m){let a=+m[1],b=+m[2],step=a<=b?1:-1;for(let i=a;;i+=step){out.push(String(i));if(i===b)break;if(out.length>1000)break;}}
    else out.push(part);
  });
  return [...new Set(out)];
}

function issue_(p) {
  const memberId=String(p.memberId||''), type=String(p.type||''), size=String(p.size||''), number=String(p.number||'').trim();
  if(!memberId||!type||!size) return {ok:false,error:'Angaben fehlen'};
  const sh=SpreadsheetApp.getActive().getSheetByName(SHEETS.ITEMS);
  const data=sh.getDataRange().getValues(); const h=data.shift();
  const idx=Object.fromEntries(h.map((x,i)=>[x,i]));
  let chosen=-1;
  for(let i=0;i<data.length;i++){
    const r=data[i];
    if(String(r[idx.type])===type && String(r[idx.size])===size && String(r[idx.status])==='available' && String(r[idx.number])===number){chosen=i;break;}
  }
  if(chosen<0) return {ok:false,error:'Dieses Kleidungsstück ist nicht verfügbar'};
  const row=chosen+2;
  sh.getRange(row,idx.status+1).setValue('loaned');
  sh.getRange(row,idx.memberId+1).setValue(memberId);
  log_('ISSUE',String(data[chosen][idx.id]),memberId);
  return {ok:true};
}

function returnItems_(p) {
  const ids=(p.itemIds||[]).map(String); if(!ids.length)return {ok:false,error:'Nichts ausgewählt'};
  const sh=SpreadsheetApp.getActive().getSheetByName(SHEETS.ITEMS);
  const data=sh.getDataRange().getValues(); const h=data.shift(); const idx=Object.fromEntries(h.map((x,i)=>[x,i]));
  const set=new Set(ids);
  for(let i=0;i<data.length;i++){
    if(set.has(String(data[i][idx.id]))){
      const row=i+2, member=String(data[i][idx.memberId]);
      sh.getRange(row,idx.status+1).setValue('available');
      sh.getRange(row,idx.memberId+1).setValue('');
      log_('RETURN',String(data[i][idx.id]),member);
    }
  }
  return {ok:true};
}

function log_(action,itemId,memberId) {
  SpreadsheetApp.getActive().getSheetByName(SHEETS.LOG).appendRow([id_(),action,itemId,memberId,now_()]);
}

function json_(o) {
  return ContentService.createTextOutput(JSON.stringify(o)).setMimeType(ContentService.MimeType.JSON);
}
