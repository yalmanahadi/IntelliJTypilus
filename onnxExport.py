from dpu_utils.utils import RichPath, run_and_debug
from pathlib import Path
from ptgnn.implementations.typilus.graph2class import Graph2Class
from ptgnn.implementations.typilus.train import load_from_folder
import os
import torch
import sys

def main(modelPath, dataPath=None):
    model_path = Path(modelPath)
    model, nn = Graph2Class.restore_model(model_path, "cpu")
    
    #data = load_from_folder(RichPath.create(os.getcwd() + "\graph-dataset"), shuffle=False)
    #print(data_input["graph_mb_data"]["node_data"]["token_idxs"].size())
    graph_mb_data_dict = {}
    graph_mb_data_dict["node_data"] = {"token_idxs" : torch.randn(17500, 5), "lengths": torch.randn(17500)}
    graph_mb_data_dict["adjacency_lists"] = [0] * 8
    graph_mb_data_dict["node_to_graph_idx"] = torch.randn(17500)
    graph_mb_data_dict["reference_node_graph_idx"] = {"token_sequence" : torch.randn(9268), "supernodes" : torch.randn(396)}
    graph_mb_data_dict["reference_node_ids"] = {"token-sequence": torch.randn(9268), "supernodes": torch.randn(396)}
    graph_mb_data_dict["num_graphs"] = 36
    target_classes = torch.randn(396)
    original_supernode_idxs = [0] * 396

    nn.eval()
    nn(graph_mb_data_dict, target_classes, original_supernode_idxs)
    #nn(data_input["graph_mb_data"], data_input["target_classes"], data_input["original_supernode_idxs"] )
    torch.onnx.export(
        nn,
        (graph_mb_data_dict, target_classes, original_supernode_idxs),
        #(data_input["graph_mb_data"], data_input["target_classes"], data_input["original_supernode_idxs"]),
        "g2c.onnx"
        #export_params=True
        #opset_version=10,
        #do_constant_folding=True
        #input_names=['graph_data']
        #output_names=['predictions']
    )


if __name__ == "__main__":
    modelPath = sys.argv[1]
    main(modelPath)
    
