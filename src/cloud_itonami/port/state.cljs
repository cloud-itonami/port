(ns cloud-itonami.port.state
  "App state for the port (port-infra-p0rt7890) appview UI. Ported 1:1 from the
  former appview/port-infra-p0rt7890/svelte/src/routes/+page.svelte template
  shell — a single static screen describing the app surface (title / project
  / routes / bindings / source path). Single reagent atom, murakumo-studio構成."
  (:require [reagent.core :as r]))

(defonce state
  (r/atom
   {:app {:title "Port Infra P0rt7890"
          :project "etzhayyim-project-port"
          :name "port-infra-p0rt7890"
          :kind "appview"
          :route-count 0
          :routes []
          :vars []
          :xrpc? true
          :relative-path "60-apps/etzhayyim-project-port/appview/port-infra-p0rt7890/svelte/src/routes/+page.svelte"}}))
