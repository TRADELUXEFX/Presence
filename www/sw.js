// SW v1773796104
const V = '1773796104';
self.addEventListener('install', function(e) { e.waitUntil(self.skipWaiting()); });
self.addEventListener('activate', function(e) {
  e.waitUntil(caches.keys().then(function(keys) {
    return Promise.all(keys.map(function(k){ return caches.delete(k); }));
  }).then(function() { return self.clients.claim(); }).then(function() {
    return self.clients.matchAll({type:'window',includeUncontrolled:true}).then(function(clients) {
      clients.forEach(function(c){ c.navigate(c.url); });
    });
  }));
});
self.addEventListener('fetch', function(e) {
  if (e.request.method !== 'GET') return;
  if (e.request.url.includes('supabase.co')) return;
  if (e.request.url.includes('cdn.jsdelivr.net')) return;
  if (e.request.url.includes('fonts.googleapis.com')) return;
  if (e.request.url.includes('fonts.gstatic.com')) return;
  e.respondWith(fetch(e.request,{cache:'no-store'}).catch(function(){ return new Response('Offline',{status:503}); }));
});
