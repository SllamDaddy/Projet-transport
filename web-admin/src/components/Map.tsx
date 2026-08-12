'use client';

import React, { useEffect } from 'react';
import { MapContainer, TileLayer, Marker, Circle, Polyline, useMapEvents, useMap } from 'react-leaflet';
import L from 'leaflet';

// Fix Next.js Leaflet default marker icon assets issue
const defaultIcon = L.icon({
  iconUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon.png',
  shadowUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-shadow.png',
  iconRetinaUrl: 'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.7.1/images/marker-icon-2x.png',
  iconSize: [25, 41],
  iconAnchor: [12, 41],
  popupAnchor: [1, -34],
  shadowSize: [41, 41]
});

interface Station {
  name: string;
  latitude: number;
  longitude: number;
  approachRadius: number;
  arrivalRadius: number;
}

interface MapProps {
  stations: Station[];
  onMapClick: (lat: number, lng: number) => void;
  onMarkerDrag: (index: number, lat: number, lng: number) => void;
}

// Subcomponent to handle click events on the map
function MapEventsHandler({ onMapClick }: { onMapClick: (lat: number, lng: number) => void }) {
  useMapEvents({
    click(e) {
      onMapClick(e.latlng.lat, e.latlng.lng);
    },
  });
  return null;
}

// Subcomponent to automatically center/zoom to fit all markers
function FitMarkers({ stations }: { stations: Station[] }) {
  const map = useMap();

  useEffect(() => {
    // Filter out stations with default (0,0) coordinates
    const activeStations = stations.filter(s => s.latitude !== 0 || s.longitude !== 0);
    if (activeStations.length === 0) return;

    const bounds = L.latLngBounds(
      activeStations.map(s => [s.latitude, s.longitude])
    );
    
    map.fitBounds(bounds, { padding: [50, 50], maxZoom: 15 });
  }, [stations, map]);

  return null;
}

export default function Map({ stations, onMapClick, onMarkerDrag }: MapProps) {
  // Center on Lille Flandres by default if there are no stations
  const defaultCenter: [number, number] = [50.6364, 3.0706];

  const polylinePositions = stations
    .filter(s => s.latitude !== 0 || s.longitude !== 0)
    .map(s => [s.latitude, s.longitude] as [number, number]);

  return (
    <div className="h-full w-full rounded-2xl overflow-hidden border border-dark-outline relative min-h-[300px]">
      <MapContainer
        center={polylinePositions.length > 0 ? polylinePositions[0] : defaultCenter}
        zoom={13}
        className="h-full w-full dark-leaflet-map"
        style={{ height: '100%', width: '100%' }}
      >
        <TileLayer
          attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />

        <MapEventsHandler onMapClick={onMapClick} />
        <FitMarkers stations={stations} />

        {stations.map((station, index) => {
          if (station.latitude === 0 && station.longitude === 0) return null;

          return (
            <React.Fragment key={index}>
              {/* Marqueur de station (Draggable) */}
              <Marker
                position={[station.latitude, station.longitude]}
                icon={defaultIcon}
                draggable={true}
                eventHandlers={{
                  dragend: (e) => {
                    const marker = e.target;
                    const position = marker.getLatLng();
                    onMarkerDrag(index, position.lat, position.lng);
                  },
                }}
              />

              {/* Cercle d'approche (Bleu) */}
              <Circle
                center={[station.latitude, station.longitude]}
                radius={station.approachRadius}
                pathOptions={{
                  color: '#2196F3',
                  fillColor: '#2196F3',
                  fillOpacity: 0.1,
                  weight: 1.5,
                  dashArray: '5, 5'
                }}
              />

              {/* Cercle d'arrivée à l'arrêt (Vert) */}
              <Circle
                center={[station.latitude, station.longitude]}
                radius={station.arrivalRadius}
                pathOptions={{
                  color: '#2E7D32',
                  fillColor: '#2E7D32',
                  fillOpacity: 0.2,
                  weight: 1.5
                }}
              />
            </React.Fragment>
          );
        })}

        {/* Tracé de la ligne qui relie les arrêts */}
        {polylinePositions.length > 1 && (
          <Polyline
            positions={polylinePositions}
            pathOptions={{
              color: '#1976D2',
              weight: 4,
              opacity: 0.7
            }}
          />
        )}
      </MapContainer>
      <div className="absolute bottom-2 left-2 bg-dark-surface/90 backdrop-blur-sm border border-dark-outline rounded-lg px-2.5 py-1.5 text-[10px] text-dark-on-surface-variant font-semibold z-[1000] pointer-events-none space-y-0.5">
        <div className="flex items-center gap-1.5">
          <span className="inline-block w-2.5 h-2.5 rounded-full bg-blue-500/20 border border-blue-500" />
          <span>Zone d’approche (Annonce vocale)</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="inline-block w-2.5 h-2.5 rounded-full bg-green-700/30 border border-green-600" />
          <span>Zone d’arrêt (Validation trajet)</span>
        </div>
      </div>
    </div>
  );
}
