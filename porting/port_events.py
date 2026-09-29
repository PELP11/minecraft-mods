"""EventBus 7 (Forge) listener calls -> NeoForge via a per-mod Events helper. Usage: python3 port_events.py <java root> <base package>"""
import os
import re
import sys

root, pkg = sys.argv[1], sys.argv[2]
CLS = r'(\b[A-Z]\w*(?:\.[A-Z]\w*)*)'

HELPER = '''package %s.event;

import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** Game-bus listeners; a Predicate listener cancels the event by returning true. */
public final class Events {
    private Events() {
    }

    public static <E extends Event> void listen(Class<E> type, Consumer<E> listener) {
        listen(EventPriority.NORMAL, type, listener);
    }

    public static <E extends Event> void listen(EventPriority priority, Class<E> type, Consumer<E> listener) {
        NeoForge.EVENT_BUS.addListener(priority, false, type, listener);
    }

    public static <E extends Event & ICancellableEvent> void listen(Class<E> type, Predicate<E> listener) {
        listen(EventPriority.NORMAL, type, listener);
    }

    public static <E extends Event & ICancellableEvent> void listen(EventPriority priority, Class<E> type, Predicate<E> listener) {
        NeoForge.EVENT_BUS.addListener(priority, false, type, event -> {
            if (listener.test(event)) event.setCanceled(true);
        });
    }
}
''' % pkg

os.makedirs(os.path.join(root, *pkg.split('.'), 'event'), exist_ok=True)
open(os.path.join(root, *pkg.split('.'), 'event', 'Events.java'), 'w').write(HELPER)

for dirpath, _, files in os.walk(root):
    for name in files:
        if not name.endswith('.java') or name == 'Events.java':
            continue
        path = os.path.join(dirpath, name)
        text = open(path, encoding='utf-8').read()
        new = re.sub(r'TickEvent\.(\w+TickEvent)\.Post\b', r'\1.Post', text)
        new = re.sub(CLS + r'\.BUS\.addListener\(Priority\.(\w+),\s*', r'Events.listen(EventPriority.\2, \1.class, ', new)
        new = re.sub(CLS + r'\.BUS\.addListener\(', r'Events.listen(\1.class, ', new)
        new = re.sub(CLS + r'\.BUS\.post\(', 'NeoForge.EVENT_BUS.post(', new)
        if new == text:
            continue
        imports = set()
        if 'Events.listen(' in new:
            imports.add('%s.event.Events' % pkg)
        if 'EventPriority.' in new:
            imports.add('net.neoforged.bus.api.EventPriority')
        if 'NeoForge.EVENT_BUS' in new:
            imports.add('net.neoforged.neoforge.common.NeoForge')
        for tick, imp in (('ServerTickEvent', 'net.neoforged.neoforge.event.tick.ServerTickEvent'),
                          ('PlayerTickEvent', 'net.neoforged.neoforge.event.tick.PlayerTickEvent'),
                          ('ClientTickEvent', 'net.neoforged.neoforge.client.event.ClientTickEvent')):
            if tick + '.Post' in new:
                imports.add(imp)
        new = re.sub(r'^import net\.neoforged\.neoforge\.event\.TickEvent;\n', '', new, flags=re.M)
        new = re.sub(r'^import net\.minecraftforge\.eventbus\..*;\n', '', new, flags=re.M)
        new = re.sub(r'^(package [^;]+;\n)', lambda m: m.group(1) + '\n' + ''.join('import %s;\n' % i for i in sorted(imports)), new, count=1)
        open(path, 'w', encoding='utf-8').write(new)
        print('events in', os.path.relpath(path, root))
