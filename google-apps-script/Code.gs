const SPREADSHEET_ID = '17ZzSdMNK_TZcikNXEwQ6ijletxNZ22ZtllViUGR23mc';

const SHEETS = {
  MEMBERS: 'Members',
  ITEMS: 'Items',
  LOG: 'Transactions'
};

function db_() {
  return SpreadsheetApp.openById(SPREADSHEET_ID);
}

function doPost(e) {
  const lock = LockService.getScriptLock();
  lock.waitLock(15000);

  try {
    const p = JSON.parse(e.postData.contents || '{}');

    if (!checkPassword_(p.password)) {
      return json_({
        ok: false,
        error: 'Falsches Passwort'
      });
    }

    setup_();

    switch (p.action) {
      case 'summary':
        return json_({ ok: true });

      case 'bootstrap':
        return json_(bootstrap_());

      case 'addMember':
        return json_(addMember_(p));

      case 'addStock':
        return json_(addStock_(p));

      case 'issue':
        return json_(issue_(p));

      case 'return':
        return json_(returnItems_(p));

      default:
        return json_({
          ok: false,
          error: 'Unbekannte Aktion'
        });
    }
  } catch (err) {
    return json_({
      ok: false,
      error: String(err)
    });
  } finally {
    lock.releaseLock();
  }
}

function doGet(e) {
  setup_();

  return json_({
    ok: true,
    service: 'VereinsKleiderverwaltung'
  });
}

function checkPassword_(pw) {
  const expected =
    PropertiesService
      .getScriptProperties()
      .getProperty('APP_PASSWORD');

  return !!expected && pw === expected;
}

function setup_() {
  const ss = db_();

  ensureSheet_(
    ss,
    SHEETS.MEMBERS,
    ['id', 'name', 'memberNumber', 'active', 'createdAt']
  );

  ensureColumns_(
    ss.getSheetByName(SHEETS.MEMBERS),
    ['id', 'name', 'memberNumber', 'active', 'createdAt']
  );

  ensureSheet_(
    ss,
    SHEETS.ITEMS,
    ['id', 'type', 'size', 'number', 'status', 'memberId', 'createdAt']
  );

  ensureSheet_(
    ss,
    SHEETS.LOG,
    ['id', 'action', 'itemId', 'memberId', 'timestamp']
  );

  // Manuell in Google Sheets eingetragene Mitglieder und Bestände
  // werden automatisch ergänzt.
  normalizeManualMembers_();
  normalizeManualItems_();
}

function ensureSheet_(ss, name, headers) {
  let sh = ss.getSheetByName(name);

  if (!sh) {
    sh = ss.insertSheet(name);
  }

  if (sh.getLastRow() === 0) {
    sh.appendRow(headers);
  }
}

function ensureColumns_(sh, requiredHeaders) {
  if (!sh) return;

  const lastColumn = Math.max(sh.getLastColumn(), 1);
  const current = sh.getRange(1, 1, 1, lastColumn).getValues()[0].map(String);

  requiredHeaders.forEach(header => {
    if (!current.includes(header)) {
      sh.getRange(1, sh.getLastColumn() + 1).setValue(header);
      current.push(header);
    }
  });
}

/*
 * Ergänzt manuell eingegebene Lagerbestände im Blatt "Items".
 *
 * Du kannst dort neue Zeilen anlegen und nur Folgendes ausfüllen:
 *   type | size | number
 *
 * Dabei darf "number" leer sein.
 *
 * Automatisch ergänzt werden:
 *   id        -> UUID
 *   status    -> available
 *   memberId  -> bleibt leer
 *   createdAt -> aktueller Zeitstempel
 *
 * Vorhandene Werte werden NICHT überschrieben.
 */
/*
 * Ergänzt manuell eingegebene Mitglieder im Blatt "Members".
 *
 * Du kannst eine neue Zeile anlegen und nur Folgendes ausfüllen:
 *   name | memberNumber
 *
 * memberNumber ist optional.
 *
 * Automatisch ergänzt werden:
 *   id        -> UUID
 *   active    -> true
 *   createdAt -> aktueller Zeitstempel
 *
 * Vorhandene Werte werden nicht überschrieben.
 */
function normalizeManualMembers_() {
  const sh = db_().getSheetByName(SHEETS.MEMBERS);

  if (!sh || sh.getLastRow() < 2) {
    return;
  }

  ensureColumns_(sh, ['id', 'name', 'memberNumber', 'active', 'createdAt']);

  const range = sh.getDataRange();
  const data = range.getValues();
  const headers = data[0].map(String);

  const idx = {
    id: headers.indexOf('id'),
    name: headers.indexOf('name'),
    memberNumber: headers.indexOf('memberNumber'),
    active: headers.indexOf('active'),
    createdAt: headers.indexOf('createdAt')
  };

  let changed = false;
  const seenIds = new Set();

  for (let r = 1; r < data.length; r++) {
    const row = data[r];
    const name = String(row[idx.name] || '').trim();

    // Zeilen ohne Namen ignorieren.
    if (!name) {
      continue;
    }

    let currentId = String(row[idx.id] || '').trim();

    if (!currentId || seenIds.has(currentId)) {
      currentId = id_();
      row[idx.id] = currentId;
      changed = true;
    }

    seenIds.add(currentId);

    if (!String(row[idx.active] || '').trim()) {
      row[idx.active] = 'true';
      changed = true;
    }

    if (!String(row[idx.createdAt] || '').trim()) {
      row[idx.createdAt] = now_();
      changed = true;
    }
  }

  if (changed) {
    range.setValues(data);
  }
}

function normalizeManualItems_() {
  const sh = db_().getSheetByName(SHEETS.ITEMS);

  if (!sh || sh.getLastRow() < 2) {
    return;
  }

  const range = sh.getDataRange();
  const data = range.getValues();

  const headers = data[0].map(String);

  const idx = {
    id: headers.indexOf('id'),
    type: headers.indexOf('type'),
    size: headers.indexOf('size'),
    number: headers.indexOf('number'),
    status: headers.indexOf('status'),
    memberId: headers.indexOf('memberId'),
    createdAt: headers.indexOf('createdAt')
  };

  if (
    idx.id < 0 ||
    idx.type < 0 ||
    idx.size < 0 ||
    idx.status < 0 ||
    idx.createdAt < 0
  ) {
    throw new Error(
      'Im Blatt Items fehlen benötigte Spaltenüberschriften.'
    );
  }

  let changed = false;
  const seenIds = new Set();

  for (let r = 1; r < data.length; r++) {
    const row = data[r];

    const type = String(row[idx.type] || '').trim();
    const size = String(row[idx.size] || '').trim();

    // Komplett leere oder unvollständige Zeilen ignorieren.
    if (!type || !size) {
      continue;
    }

    let currentId = String(row[idx.id] || '').trim();

    // Fehlende oder doppelte IDs automatisch neu erzeugen.
    if (!currentId || seenIds.has(currentId)) {
      currentId = id_();
      row[idx.id] = currentId;
      changed = true;
    }

    seenIds.add(currentId);

    // Nur leeren Status ergänzen.
    if (!String(row[idx.status] || '').trim()) {
      row[idx.status] = 'available';
      changed = true;
    }

    // Nur leeres Erstellungsdatum ergänzen.
    if (!String(row[idx.createdAt] || '').trim()) {
      row[idx.createdAt] = now_();
      changed = true;
    }

    // memberId bleibt bei manuell neu erfassten Beständen leer.
    // Vorhandene memberId-Werte werden niemals überschrieben.
  }

  if (changed) {
    range.setValues(data);
  }
}

function rows_(name) {
  const sh = db_().getSheetByName(name);
  const values = sh.getDataRange().getValues();

  if (!values.length) {
    return [];
  }

  const headers = values.shift();

  return values
    .filter(row => row.some(v => v !== ''))
    .map(row => {
      const obj = {};

      headers.forEach((header, i) => {
        obj[header] = String(row[i] ?? '');
      });

      return obj;
    });
}

function bootstrap_() {
  // Sicherheitsnetz für direkt in Google Sheets eingetragene Daten.
  normalizeManualMembers_();
  normalizeManualItems_();

  const allItems = rows_(SHEETS.ITEMS);

  return {
    ok: true,

    members: rows_(SHEETS.MEMBERS)
      .filter(x => x.active !== 'false'),

    items: allItems,

    loans: allItems
      .filter(x => x.status === 'loaned'),

    history: buildHistory_()
  };
}

function buildHistory_() {
  const items = rows_(SHEETS.ITEMS);
  const itemMap = {};

  items.forEach(item => {
    itemMap[item.id] = item;
  });

  return rows_(SHEETS.LOG)
    .map(log => {
      const item = itemMap[log.itemId] || {};

      return {
        id: log.id || '',
        action: log.action || '',
        itemId: log.itemId || '',
        memberId: log.memberId || '',
        timestamp: log.timestamp || '',
        type: item.type || '',
        size: item.size || '',
        number: item.number || ''
      };
    })
    .sort((a, b) => String(b.timestamp).localeCompare(String(a.timestamp)));
}

function id_() {
  return Utilities.getUuid();
}

function now_() {
  return new Date().toISOString();
}

function addMember_(p) {
  const name = String(p.name || '').trim();
  const memberNumber = String(p.memberNumber || '').trim();

  if (!name) {
    return {
      ok: false,
      error: 'Name fehlt'
    };
  }

  const sh = db_().getSheetByName(SHEETS.MEMBERS);
  ensureColumns_(sh, ['id', 'name', 'memberNumber', 'active', 'createdAt']);

  const headers = sh.getRange(1, 1, 1, sh.getLastColumn()).getValues()[0].map(String);
  const row = new Array(headers.length).fill('');

  row[headers.indexOf('id')] = id_();
  row[headers.indexOf('name')] = name;
  row[headers.indexOf('memberNumber')] = memberNumber;
  row[headers.indexOf('active')] = 'true';
  row[headers.indexOf('createdAt')] = now_();

  sh.appendRow(row);

  return {
    ok: true
  };
}

function addStock_(p) {
  const type = String(p.type || '').trim();
  const size = String(p.size || '').trim();

  if (!type || !size) {
    return {
      ok: false,
      error: 'Art und Größe fehlen'
    };
  }

  const nums = String(p.numbers || '').trim();
  let numbers = [];

  if (nums) {
    numbers = parseNumbers_(nums);
  } else {
    const count =
      Math.max(
        0,
        parseInt(p.count || '0', 10) || 0
      );

    for (let i = 0; i < count; i++) {
      numbers.push('');
    }
  }

  if (!numbers.length) {
    return {
      ok: false,
      error: 'Bitte Nummern oder eine Anzahl angeben'
    };
  }

  const sh =
    db_().getSheetByName(SHEETS.ITEMS);

  const existing = rows_(SHEETS.ITEMS);

  const keys = new Set(
    existing
      .filter(x => x.type === type)
      .map(x => x.number)
  );

  for (const n of numbers) {
    if (
      n &&
      type === 'Trikot' &&
      keys.has(n)
    ) {
      return {
        ok: false,
        error: 'Nummer ' + n + ' ist bereits im Bestand'
      };
    }
  }

  numbers.forEach(number => {
    sh.appendRow([
      id_(),
      type,
      size,
      number,
      'available',
      '',
      now_()
    ]);
  });

  return {
    ok: true,
    added: numbers.length
  };
}

function parseNumbers_(s) {
  const out = [];

  s.split(',')
    .map(x => x.trim())
    .filter(Boolean)
    .forEach(part => {
      const m =
        part.match(/^(\d+)\s*-\s*(\d+)$/);

      if (m) {
        let start = Number(m[1]);
        let end = Number(m[2]);
        const step =
          start <= end ? 1 : -1;

        for (
          let i = start;
          ;
          i += step
        ) {
          out.push(String(i));

          if (i === end) {
            break;
          }

          if (out.length > 1000) {
            break;
          }
        }
      } else {
        out.push(part);
      }
    });

  return [...new Set(out)];
}

function issue_(p) {
  normalizeManualItems_();

  const memberId =
    String(p.memberId || '');

  const type =
    String(p.type || '');

  const size =
    String(p.size || '');

  const number =
    String(p.number || '').trim();

  if (!memberId || !type || !size) {
    return {
      ok: false,
      error: 'Angaben fehlen'
    };
  }

  const sh =
    db_().getSheetByName(SHEETS.ITEMS);

  const data =
    sh.getDataRange().getValues();

  const headers = data.shift();

  const idx =
    Object.fromEntries(
      headers.map((x, i) => [x, i])
    );

  let chosen = -1;

  for (
    let i = 0;
    i < data.length;
    i++
  ) {
    const row = data[i];

    if (
      String(row[idx.type]) === type &&
      String(row[idx.size]) === size &&
      String(row[idx.status]) === 'available' &&
      String(row[idx.number]) === number
    ) {
      chosen = i;
      break;
    }
  }

  if (chosen < 0) {
    return {
      ok: false,
      error: 'Dieses Kleidungsstück ist nicht verfügbar'
    };
  }

  const sheetRow =
    chosen + 2;

  sh.getRange(
    sheetRow,
    idx.status + 1
  ).setValue('loaned');

  sh.getRange(
    sheetRow,
    idx.memberId + 1
  ).setValue(memberId);

  log_(
    'ISSUE',
    String(data[chosen][idx.id]),
    memberId
  );

  return {
    ok: true
  };
}

function returnItems_(p) {
  const ids =
    (p.itemIds || []).map(String);

  if (!ids.length) {
    return {
      ok: false,
      error: 'Nichts ausgewählt'
    };
  }

  const sh =
    db_().getSheetByName(SHEETS.ITEMS);

  const data =
    sh.getDataRange().getValues();

  const headers =
    data.shift();

  const idx =
    Object.fromEntries(
      headers.map((x, i) => [x, i])
    );

  const wanted =
    new Set(ids);

  for (
    let i = 0;
    i < data.length;
    i++
  ) {
    if (
      wanted.has(
        String(data[i][idx.id])
      )
    ) {
      const sheetRow =
        i + 2;

      const memberId =
        String(
          data[i][idx.memberId]
        );

      sh.getRange(
        sheetRow,
        idx.status + 1
      ).setValue('available');

      sh.getRange(
        sheetRow,
        idx.memberId + 1
      ).setValue('');

      log_(
        'RETURN',
        String(
          data[i][idx.id]
        ),
        memberId
      );
    }
  }

  return {
    ok: true
  };
}

function log_(action, itemId, memberId) {
  db_()
    .getSheetByName(SHEETS.LOG)
    .appendRow([
      id_(),
      action,
      itemId,
      memberId,
      now_()
    ]);
}

function json_(obj) {
  return ContentService
    .createTextOutput(
      JSON.stringify(obj)
    )
    .setMimeType(
      ContentService.MimeType.JSON
    );
}

function testZugriff() {
  const ss = SpreadsheetApp.openById(
    SPREADSHEET_ID
  );

  Logger.log(ss.getName());
}
