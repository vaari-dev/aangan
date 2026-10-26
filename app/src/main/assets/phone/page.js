// The phone page's two conveniences. The page works without them.
(() => {
  // Text goes in as text, never as HTML.
  const el = (tag, cls, text) => {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text) e.textContent = text;
    return e;
  };

  // The dock: choosing an app that's already in another place swaps the two, as on the TV.
  const slots = [...document.querySelectorAll('.dock select')];
  const before = new Map(slots.map(s => [s, s.value]));
  for (const s of slots) {
    s.addEventListener('change', () => {
      const other = slots.find(o => o !== s && s.value && o.value === s.value);
      if (other) {
        other.value = before.get(s);
        before.set(other, other.value);
      }
      before.set(s, s.value);
    });
  }

  // The city: places appear as you type, from the TV's own search.
  const form = document.querySelector('form.search');
  if (!form) return;
  const input = form.querySelector('input[name=q]');
  const base = form.getAttribute('action').replace(/\/city$/, '');
  // The words, in the TV's language, come with the page: data-searching, data-failed and so on.
  const say = form.dataset;
  const status = el('p', 'sr');
  status.setAttribute('aria-live', 'polite');
  const list = el('ul', 'card list suggest');
  list.id = 'suggestions';
  list.hidden = true;
  input.setAttribute('aria-controls', list.id);
  input.setAttribute('aria-expanded', 'false');
  form.closest('section').append(status, list);
  let timer;
  let asked = 0;

  input.addEventListener('input', () => {
    clearTimeout(timer);
    const q = input.value.trim();
    if (q.length < 2) {
      asked++;
      show(null, '');
      return;
    }
    timer = setTimeout(async () => {
      const ask = ++asked;
      status.textContent = say.searching;
      let places = null;
      try {
        const r = await fetch(base + '/city.json?q=' + encodeURIComponent(q));
        if (r.ok) places = await r.json();
      } catch (e) {
        places = null;
      }
      if (ask === asked) show(places, q);
    }, 350);
  });

  function show(places, q) {
    list.replaceChildren();
    if (!q) {
      list.hidden = true;
      input.setAttribute('aria-expanded', 'false');
      status.textContent = '';
      return;
    }
    let message = '';
    if (!places) message = say.failed;
    else if (!places.length) message = say.none.replace('{q}', q);
    if (message) {
      const li = el('li', 'item');
      li.append(el('span', 'text', message));
      list.append(li);
    }
    for (const p of places || []) {
      const f = el('form');
      f.method = 'post';
      f.action = base + '/city/set';
      for (const [name, value] of [['name', p.name], ['region', p.region], ['lat', p.lat], ['lon', p.lon], ['tz', p.tz], ['cc', p.cc]]) {
        const i = el('input');
        i.type = 'hidden';
        i.name = name;
        i.value = value;
        f.append(i);
      }
      const b = el('button', 'place');
      const text = el('span');
      text.append(el('b', '', p.name), el('span', '', p.about));
      b.append(text, el('span', 'chev go'));
      f.append(b);
      const li = el('li');
      li.append(f);
      list.append(li);
    }
    list.hidden = false;
    input.setAttribute('aria-expanded', 'true');
    status.textContent = message || (places.length === 1 ? say.one : say.many.replace('{n}', places.length));
  }
})();
