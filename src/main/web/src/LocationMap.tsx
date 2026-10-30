import { useEffect, useRef, useState } from "react";
import L from "leaflet";
import { LocateFixed } from "lucide-react";
import type { Coordinate, Location } from "./model";
import "leaflet/dist/leaflet.css";

type Props = {
  locations: Record<string, Location>;
  selected: string;
  picking: boolean;
  onSelect: (key: string) => void;
  onPick: (coordinate: Coordinate) => void;
};

function isMappable(location: Location): location is Coordinate {
  return (
    location.type === "coordinate" &&
    Number.isFinite(location.lat) &&
    Number.isFinite(location.lon) &&
    Math.abs(location.lat) <= 90 &&
    Math.abs(location.lon) <= 180
  );
}

export function LocationMap(props: Props) {
  const container = useRef<HTMLDivElement>(null);
  const mapRef = useRef<L.Map | null>(null);
  const callbacks = useRef(props);
  const [tilesUnavailable, setTilesUnavailable] = useState(false);
  callbacks.current = props;

  function fitLocations(map: L.Map) {
    const coordinates = Object.values(callbacks.current.locations).filter(
      isMappable,
    );
    if (coordinates.length)
      map.fitBounds(
        L.latLngBounds(
          coordinates.map((location) => [location.lat, location.lon]),
        ),
        { padding: [40, 40], maxZoom: 15 },
      );
  }

  useEffect(() => {
    const map = L.map(container.current!, { worldCopyJump: true }).setView(
      [39, -98],
      4,
    );
    mapRef.current = map;
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution:
        '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      maxZoom: 19,
    })
      .on("tileerror", () => setTilesUnavailable(true))
      .addTo(map);
    map.on("click", (event) => {
      if (!callbacks.current.picking) return;
      onCoordinate(event.latlng);
    });
    function onCoordinate(latlng: L.LatLng) {
      callbacks.current.onPick({
        type: "coordinate",
        lat: Number(latlng.lat.toFixed(6)),
        lon: Number(latlng.wrap().lng.toFixed(6)),
      });
    }
    fitLocations(map);
    const resize = new ResizeObserver(() => map.invalidateSize());
    resize.observe(container.current!);
    return () => {
      resize.disconnect();
      map.remove();
      mapRef.current = null;
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current!;
    const markers = L.layerGroup().addTo(map);
    for (const [key, location] of Object.entries(props.locations)) {
      if (!isMappable(location)) continue;
      const active = key === props.selected;
      const label = document.createElement("span");
      label.textContent = location.name || key;
      L.circleMarker([location.lat, location.lon], {
        radius: active ? 10 : 6,
        color: active ? "#1c463e" : "#fff",
        weight: 2,
        fillColor: active ? "#e8b45b" : "#1c463e",
        fillOpacity: 1,
        bubblingMouseEvents: false,
      })
        .bindTooltip(label)
        .on("click", () => callbacks.current.onSelect(key))
        .addTo(markers);
    }
    return () => {
      markers.remove();
    };
  }, [props.locations, props.selected]);

  const selected = props.locations[props.selected];
  const lat = selected && isMappable(selected) ? selected.lat : undefined;
  const lon = selected && isMappable(selected) ? selected.lon : undefined;
  useEffect(() => {
    if (
      lat === undefined ||
      lon === undefined ||
      !Number.isFinite(lat) ||
      !Number.isFinite(lon)
    )
      return;
    mapRef.current!.setView(
      [lat, lon],
      Math.max(mapRef.current!.getZoom(), 12),
      { animate: false },
    );
  }, [props.selected, lat, lon]);

  return (
    <div className={`map-frame ${props.picking ? "picking" : ""}`}>
      <div
        ref={container}
        className="location-map"
        aria-label="Location selection map. Pan and zoom, then click to set coordinates. Coordinate inputs are also available below."
      />
      <button
        className="map-fit button secondary compact"
        onClick={() => fitLocations(mapRef.current!)}
      >
        <LocateFixed size={16} /> Fit locations
      </button>
      <div className="map-caption">
        {props.picking
          ? "Click the map to place the selected location"
          : "Select a location to inspect its coordinates"}
      </div>
      {tilesUnavailable && (
        <p className="map-notice">
          Map tiles could not load. You can still enter coordinates below.
        </p>
      )}
    </div>
  );
}
